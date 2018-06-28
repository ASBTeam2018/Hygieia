package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitType;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.PlainGitRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.GitRequestRepository;
import com.capitalone.dashboard.repository.PlainGitRepoRepository;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CollectorTask that fetches Commit information from PlainGit
 */
@Component
public class PlainGitCollectorTask extends CollectorTask<Collector> {
    private static final Log LOG = LogFactory.getLog(PlainGitCollectorTask.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final PlainGitRepoRepository plainGitRepoRepository;
    private final CommitRepository commitRepository;
    private final GitRequestRepository gitRequestRepository;
    private final PlainGitClient plainGitClient;
    private final PlainGitSettings plainGitSettings;
    private final ComponentRepository dbComponentRepository;
    private static final long FOURTEEN_DAYS_MILLISECONDS = 14 * 24 * 60 * 60 * 1000;
    private static final String API_RATE_LIMIT_MESSAGE = "API rate limit exceeded";
    private List<Pattern> commitExclusionPatterns = new ArrayList<>();

    @Autowired
    public PlainGitCollectorTask(TaskScheduler taskScheduler,
                               BaseCollectorRepository<Collector> collectorRepository,
                               PlainGitRepoRepository plainGitRepoRepository,
                               CommitRepository commitRepository,
                               GitRequestRepository gitRequestRepository,
                               PlainGitClient plainGitClient,
                               PlainGitSettings plainGitSettings,
                               ComponentRepository dbComponentRepository) {
        super(taskScheduler, "GitHub");
        this.collectorRepository = collectorRepository;
        this.plainGitRepoRepository = plainGitRepoRepository;
        this.commitRepository = commitRepository;
        this.plainGitClient = plainGitClient;
        this.plainGitSettings = plainGitSettings;
        this.dbComponentRepository = dbComponentRepository;
        this.gitRequestRepository = gitRequestRepository;
        if (!CollectionUtils.isEmpty(plainGitSettings.getNotBuiltCommits())) {
            for (String regExStr : plainGitSettings.getNotBuiltCommits()) {
                Pattern pattern = Pattern.compile(regExStr, Pattern.CASE_INSENSITIVE);
                commitExclusionPatterns.add(pattern);
            }
        }
    }

    @Override
    public Collector getCollector() {
        Collector protoType = new Collector();
        protoType.setName("GitHub");
        protoType.setCollectorType(CollectorType.SCM);
        protoType.setOnline(true);
        protoType.setEnabled(true);

        Map<String, Object> allOptions = new HashMap<>();
        allOptions.put(PlainGitRepo.REPO_URL, "");
        allOptions.put(PlainGitRepo.BRANCH, "");
        allOptions.put(PlainGitRepo.USER_ID, "");
        allOptions.put(PlainGitRepo.PASSWORD, "");
        allOptions.put(PlainGitRepo.PERSONAL_ACCESS_TOKEN, "");
        protoType.setAllFields(allOptions);

        Map<String, Object> uniqueOptions = new HashMap<>();
        uniqueOptions.put(PlainGitRepo.REPO_URL, "");
        uniqueOptions.put(PlainGitRepo.BRANCH, "");
        protoType.setUniqueFields(uniqueOptions);
        return protoType;
    }

    @Override
    public BaseCollectorRepository<Collector> getCollectorRepository() {
        return collectorRepository;
    }

    @Override
    public String getCron() {
        return plainGitSettings.getCron();
    }

    /**
     * Clean up unused deployment collector items
     *
     * @param collector the {@link Collector}
     */
    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts") // agreed, fixme
    private void clean(Collector collector) {
        Set<ObjectId> uniqueIDs = new HashSet<>();
        /**
         * Logic: For each component, retrieve the collector item list of the type SCM.
         * Store their IDs in a unique set ONLY if their collector IDs match with GitHub collectors ID.
         */
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository.findAll()) {
            if (comp.getCollectorItems() != null && !comp.getCollectorItems().isEmpty()) {
                List<CollectorItem> itemList = comp.getCollectorItems().get(CollectorType.SCM);
                if (itemList != null) {
                    for (CollectorItem ci : itemList) {
                        if (ci != null && ci.getCollectorId().equals(collector.getId())) {
                            uniqueIDs.add(ci.getId());
                        }
                    }
                }
            }
        }

        /**
         * Logic: Get all the collector items from the collector_item collection for this collector.
         * If their id is in the unique set (above), keep them enabled; else, disable them.
         */
        List<PlainGitRepo> repoList = new ArrayList<>();
        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(collector.getId());
        for (PlainGitRepo repo : plainGitRepoRepository.findByCollectorIdIn(gitID)) {
            if (repo != null) {
                repo.setEnabled(uniqueIDs.contains(repo.getId()));
                repoList.add(repo);
            }
        }
        plainGitRepoRepository.save(repoList);
    }


    @Override
    public void collect(Collector collector) {

        logBanner("Starting...");
        long start = System.currentTimeMillis();
        int repoCount = 0;
        int commitCount = 0;
        int pullCount = 0;
        int issueCount = 0;

        clean(collector);
        for (PlainGitRepo repo : enabledRepos(collector)) {
            if (repo.getErrorCount() < plainGitSettings.getErrorThreshold()) {
                boolean firstRun = ((repo.getLastUpdated() == 0) || ((start - repo.getLastUpdated()) > FOURTEEN_DAYS_MILLISECONDS));
                repo.removeLastUpdateDate();  //moved last update date to collector item. This is to clean old data.
                try {
                    LOG.info(repo.getOptions().toString() + "::" + repo.getBranch() + ":: get commits");
                    // Step 1: Get all the commits
                    for (Commit commit : plainGitClient.getCommits(repo, firstRun, commitExclusionPatterns)) {
                        LOG.debug(commit.getTimestamp() + ":::" + commit.getScmCommitLog());
                        if (isNewCommit(repo, commit)) {
                            commit.setCollectorItemId(repo.getId());
                            commitRepository.save(commit);
                            commitCount++;
                        }
                    }

                    // Step 2: Get all the issues
                    LOG.info(repo.getOptions().toString() + "::" + repo.getBranch() + " get issues");
                    List<GitRequest> issues = plainGitClient.getIssues(repo, firstRun);
                    issueCount += processList(repo, issues, "issue");

                    //Step 2: Get all the Pull Requests
                    LOG.info(repo.getOptions().toString() + "::" + repo.getBranch() + "::get pulls");
                    List<GitRequest> allPRs = gitRequestRepository.findRequestNumberAndLastUpdated(repo.getId(), "pull");

                    Map<Long, String> prCloseMap = allPRs.stream().collect(
                            Collectors.toMap(GitRequest::getUpdatedAt, GitRequest::getNumber,
                                    (oldValue, newValue) -> oldValue
                            )
                    );
                    List<GitRequest> pulls = plainGitClient.getPulls(repo, "all", firstRun, prCloseMap);
                    pullCount += processList(repo, pulls, "pull");

                    repo.setLastUpdated(System.currentTimeMillis());
                } catch (HttpStatusCodeException hc) {
                    LOG.error("Error fetching commits for:" + repo.getRepoUrl(), hc);
                    if (! (isRateLimitError(hc) || hc.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) ) {
                        CollectionError error = new CollectionError(hc.getStatusCode().toString(), hc.getMessage());
                        repo.getErrors().add(error);
                    }
                } catch (ResourceAccessException ex) {
                    //handle case where repo is valid but github returns connection refused due to outages??
                    if (ex.getMessage() != null && ex.getMessage().contains("Connection refused")) {
                        LOG.error("Error fetching commits for:" + repo.getRepoUrl(), ex);
                    } else {
                        LOG.error("Error fetching commits for:" + repo.getRepoUrl(), ex);
                        CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, repo.getRepoUrl());
                        repo.getErrors().add(error);
                    }
                } catch (RestClientException | MalformedURLException ex) {
                    LOG.error("Error fetching commits for:" + repo.getRepoUrl(), ex);
                    CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, repo.getRepoUrl());
                    repo.getErrors().add(error);
                } catch (HygieiaException he) {
                    LOG.error("Error fetching commits for:" + repo.getRepoUrl(), he);
                    CollectionError error = new CollectionError("Bad repo url", repo.getRepoUrl());
                    repo.getErrors().add(error);
                }
                plainGitRepoRepository.save(repo);
            }
            repoCount++;
        }
        log("Repo Count", start, repoCount);
        log("New Commits", start, commitCount);
        log("New Pulls", start, pullCount);
        log("New Issues", start, issueCount);

        log("Finished", start);
    }


    private int processList(PlainGitRepo repo, List<GitRequest> entries, String type) {
        int count = 0;
        if (CollectionUtils.isEmpty(entries)) return 0;

        for (GitRequest entry : entries) {
            LOG.debug(entry.getTimestamp() + ":::" + entry.getScmCommitLog());
            GitRequest existing = gitRequestRepository.findByCollectorItemIdAndNumberAndRequestType(repo.getId(), entry.getNumber(), type);

            if (existing == null) {
                entry.setCollectorItemId(repo.getId());
                count++;
            } else {
                entry.setId(existing.getId());
                entry.setCollectorItemId(repo.getId());
            }
            gitRequestRepository.save(entry);

            //fix merge commit type for squash merged and rebased merged PRs
            //PRs that were squash merged or rebase merged have only one parent
            if ("pull".equalsIgnoreCase(type) && "merged".equalsIgnoreCase(entry.getState())) {
                List<Commit> commits = commitRepository.findByScmRevisionNumber(entry.getScmRevisionNumber());
                for(Commit commit : commits) {
                    if (commit.getType() != null) {
                        if (commit.getType() != CommitType.Merge) {
                            commit.setType(CommitType.Merge);
                            commitRepository.save(commit);
                        }
                    } else {
                        commit.setType(CommitType.Merge);
                        commitRepository.save(commit);
                    }
                }
            }
        }
        return count;
    }

    private boolean isRateLimitError(HttpStatusCodeException hc) {
        String response = hc.getResponseBodyAsString();
        return StringUtils.isEmpty(response) ? false : response.contains(API_RATE_LIMIT_MESSAGE);
    }

    private List<PlainGitRepo> enabledRepos(Collector collector) {
        return plainGitRepoRepository.findEnabledPlainGitRepos(collector.getId());
    }


    private boolean isNewCommit(PlainGitRepo repo, Commit commit) {
        return commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo.getId(), commit.getScmRevisionNumber()) == null;
    }

    private GitRequest getExistingRequest(PlainGitRepo repo, GitRequest request, String type) {
        return gitRequestRepository.findByCollectorItemIdAndNumberAndRequestType(
                repo.getId(), request.getNumber(), type);
    }
}
