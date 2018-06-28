package com.capitalone.dashboard.collector;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bean to hold settings specific to the PlainGit collector.
 */
@Component
@ConfigurationProperties(prefix = "plaingit")
public class PlainGitSettings {
    private String cron;
    private String host;
    private String key;
	@Value("${plaingit.firstRunHistoryDays:14}")
    private int firstRunHistoryDays;
    private List<String> notBuiltCommits;
	@Value("${plaingit.errorThreshold:2}")
    private int errorThreshold;
	@Value("${plaingit.rateLimitThreshold:10}")
	private int rateLimitThreshold;
	private String personalAccessToken;
	
	// plainGit
	private String localRepoPath;
	private String localRepoGitConfig;
	private String remoteRepoURI;
	private String localCodeDir;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

	public String getKey() {
		return key;
	}
	
	public void setKey(String key) {
		this.key = key;
	}
	
    public int getFirstRunHistoryDays() {
		return firstRunHistoryDays;
	}

	public void setFirstRunHistoryDays(int firstRunHistoryDays) {
		this.firstRunHistoryDays = firstRunHistoryDays;
	}

    public List<String> getNotBuiltCommits() {
        return notBuiltCommits;
    }

    public void setNotBuiltCommits(List<String> notBuiltCommits) {
        this.notBuiltCommits = notBuiltCommits;
    }

	public int getErrorThreshold() {
		return errorThreshold;
	}

	public void setErrorThreshold(int errorThreshold) {
		this.errorThreshold = errorThreshold;
	}

	public int getRateLimitThreshold() {
		return rateLimitThreshold;
	}

	public void setRateLimitThreshold(int rateLimitThreshold) {
		this.rateLimitThreshold = rateLimitThreshold;
	}

	public String getPersonalAccessToken() {
		return personalAccessToken;
	}

	public void setPersonalAccessToken(String personalAccessToken) {
		this.personalAccessToken = personalAccessToken;
	}

	public String getLocalRepoPath() {
		return localRepoPath;
	}

	public void setLocalRepoPath(String localRepoPath) {
		this.localRepoPath = localRepoPath;
	}

	public String getLocalRepoGitConfig() {
		return localRepoGitConfig;
	}

	public void setLocalRepoGitConfig(String localRepoGitConfig) {
		this.localRepoGitConfig = localRepoGitConfig;
	}

	public String getRemoteRepoURI() {
		return remoteRepoURI;
	}

	public void setRemoteRepoURI(String remoteRepoURI) {
		this.remoteRepoURI = remoteRepoURI;
	}

	public String getLocalCodeDir() {
		return localCodeDir;
	}

	public void setLocalCodeDir(String localCodeDir) {
		this.localCodeDir = localCodeDir;
	}
	
}
