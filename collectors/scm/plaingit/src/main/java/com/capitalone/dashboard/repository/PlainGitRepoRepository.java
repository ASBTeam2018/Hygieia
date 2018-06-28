package com.capitalone.dashboard.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import com.capitalone.dashboard.model.PlainGitRepo;

import java.util.List;

public interface PlainGitRepoRepository extends BaseCollectorItemRepository<PlainGitRepo> {

    @Query(value="{ 'collectorId' : ?0, options.repoUrl : ?1, options.branch : ?2}")
    PlainGitRepo findGitHubRepo(ObjectId collectorId, String url, String branch);

    @Query(value="{ 'collectorId' : ?0, enabled: true}")
    List<PlainGitRepo> findEnabledPlainGitRepos(ObjectId collectorId);
}
