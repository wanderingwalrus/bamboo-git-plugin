package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

public class GitOperationHelperFactory
{
    private GitOperationHelperFactory()
    {
    }

    public static GitOperationHelper createGitOperationHelper(final @NotNull GitRepository repository,
                                                              final @NotNull GitRepository.GitRepositoryAccessData accessData,
                                                              final @NotNull SshProxyService sshProxyService,
                                                              final @NotNull BuildLogger buildLogger,
                                                              final @NotNull TextProvider textProvider) throws RepositoryException
    {
        if (isNativeGitEnabled(repository))
        {
            return new NativeGitOperationHelper(repository, accessData, sshProxyService, buildLogger, textProvider);
        }
        else
        {
            return new JGitOperationHelper(accessData, buildLogger, textProvider);
        }
    }

    public static boolean isNativeGitEnabled(final GitRepository repository)
    {
        return StringUtils.isNotBlank(repository.getGitCapability());
    }
}
