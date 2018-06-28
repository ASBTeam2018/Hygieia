package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.PlainGitRepo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.springframework.web.client.RestClientException;

/**
 * Client for fetching commit history from PlainGit
 */
public interface PlainGitClient {


	List<Commit> getCommits(PlainGitRepo repo, boolean firstRun, List<Pattern> commitExclusionPatterns) throws MalformedURLException, HygieiaException, RestClientException, IOException, NoWorkTreeException, GitAPIException;

    List<GitRequest> getPulls(PlainGitRepo repo, String status, boolean firstRun, Map<Long, String> prMap) throws MalformedURLException, HygieiaException;

    List<GitRequest> getIssues(PlainGitRepo repo, boolean firstRun) throws MalformedURLException, HygieiaException;

}
