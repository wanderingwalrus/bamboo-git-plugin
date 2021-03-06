package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plugins.git.testutils.ExtractComments;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.testtools.ZipResourceDirectory;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

// sequentialChangeDetections need sequence from dataProvider
@Test(singleThreaded = true)
public class WalkingRepositoryWithMultipleBranchesTest extends GitAbstractTest
{
    /* repository structure:
        * 5d3d87a commit 5 on second
        * 459eabf commit 3 on second
        | * 2c389a8 commit 4 on master
        | * 2244620 commit 2 on master
        |/
        * 5db8cd0 initial commit
     */
    private static final String CHG_M_1 = "5db8cd0cb0724c3e95fec0d64ac97c80a27f5671";
    private static final String CHG_M_2 = "2244620c0963b428c2f4a2c4a7d0b653a0a33ac1";
    private static final String CHG_S_3 = "459eabfc65e71799ac4de990711a2cd963d6c485";
    private static final String CHG_M_4 = "2c389a83e43c140eae2fddb9d794f79af6124214";
    private static final String CHG_S_5 = "5d3d87a7cd7feb3413a5f4b3e67c429b1758d53a";
    private static final String COMMENT_M_1 = "initial commit";
    private static final String COMMENT_M_2 = "commit 2 on master";
    private static final String COMMENT_S_3 = "commit 3 on second";
    private static final String COMMENT_M_4 = "commit 4 on master";
    private static final String COMMENT_S_5 = "commit 5 on second";

    private File sourceRepositoriesBase;

    @BeforeClass
    public void setUpTmpDirWithRepository() throws Exception
    {
        sourceRepositoriesBase = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("multiple-branches-with-conflicts.zip", sourceRepositoriesBase);
    }

    @DataProvider
    Object[][] subsequentChangeDetectionsData()
    {
        return new Object[][]{
                {"master", null, "1", CHG_M_1, Collections.<String>emptyList()}, // initial builds, notice branches
                {"master", null, "2", CHG_M_2, Collections.<String>emptyList()},
                {"master", null, "3", CHG_M_2, Collections.<String>emptyList()},
                {"master", null, "4", CHG_M_4, Collections.<String>emptyList()},
                {"master", null, "5", CHG_M_4, Collections.<String>emptyList()},
                {"second", null, "3", CHG_S_3, Collections.<String>emptyList()},
                {"second", null, "4", CHG_S_3, Collections.<String>emptyList()},
                {"second", null, "5", CHG_S_5, Collections.<String>emptyList()},

                {"master", CHG_M_1, "2", CHG_M_2, asList(COMMENT_M_2)}, // subsequent sequential builds
                {"master", CHG_M_1, "3", CHG_M_2, asList(COMMENT_M_2)},
                {"master", CHG_M_1, "4", CHG_M_4, asList(COMMENT_M_4, COMMENT_M_2)},
                {"master", CHG_M_1, "5", CHG_M_4, asList(COMMENT_M_4, COMMENT_M_2)},
                {"second", CHG_M_1, "3", CHG_S_3, asList(COMMENT_S_3)},
                {"second", CHG_M_1, "4", CHG_S_3, asList(COMMENT_S_3)},
                {"second", CHG_M_1, "5", CHG_S_5, asList(COMMENT_S_5, COMMENT_S_3)},

//                {"master", CHG_S_3, "4", CHG_M_4, asList(COMMENT_M_4)}, // switch second -> master = not supported if not in cache!
//                {"second", CHG_M_4, "5", CHG_S_5, asList(COMMENT_S_5)},

        };
    }

    @Test(dataProvider = "subsequentChangeDetectionsData")
    public void testSubsequentChangeDetections(String branch, String previousChangeset, String srcRepo, @Nullable String expectedHead, List<String> expectedComments) throws Exception
    {
        File source = new File(sourceRepositoriesBase, srcRepo);
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, source, branch);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", previousChangeset);
        String vcsRevisionKey = changes.getVcsRevisionKey();
        Assert.assertEquals(vcsRevisionKey, expectedHead);

        List<String> comments = Lists.transform(changes.getChanges(), new ExtractComments());
        Assert.assertEquals(comments, expectedComments);
    }

    @DataProvider
    Object[][] subsequentChangeDetectionsDataFailing()
    {
        return new Object[][]{
                {"second", null, "1", Collections.<String>emptyList()},
                {"second", null, "2", Collections.<String>emptyList()},
        };
    }

    @Test(dataProvider = "subsequentChangeDetectionsDataFailing", expectedExceptions = RepositoryException.class, expectedExceptionsMessageRegExp = "Cannot determine head revision.*")
    public void testSubsequentChangeDetectionsFailing(String branch, String previousChangeset, String srcRepo, List<String> expectedComments) throws Exception
    {
        testSubsequentChangeDetections(branch, previousChangeset, srcRepo, null, expectedComments);
    }

    @Test(dataProvider = "subsequentChangeDetectionsData")
    public void testSubsequentChangeDetectionsWithCache(String branch, String previousChangeset, String srcRepo, @Nullable String expectedHead, List<String> expectedComments) throws Exception
    {
        File source = new File(sourceRepositoriesBase, srcRepo);
        File singleSource = new File(sourceRepositoriesBase, "testSubsequentChangeDetectionsWithCache_Repo");
        FileUtils.deleteQuietly(singleSource);
        FileUtils.forceMkdir(singleSource);
        FileUtils.copyDirectory(source, singleSource);

        File workingDir = new File(sourceRepositoriesBase, "testSubsequentChangeDetectionsWithCache_WorkDir");
        FileUtils.forceMkdir(workingDir);

        GitRepository gitRepository = createGitRepository();
        gitRepository.setWorkingDir(workingDir);
        setRepositoryProperties(gitRepository, singleSource, branch);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", previousChangeset);
        String vcsRevisionKey = changes.getVcsRevisionKey();
        Assert.assertEquals(vcsRevisionKey, expectedHead);

        List<String> comments = Lists.transform(changes.getChanges(), new ExtractComments());
        Assert.assertEquals(comments, expectedComments);

    }

    @Test(dataProvider = "subsequentChangeDetectionsDataFailing", expectedExceptions = RepositoryException.class, expectedExceptionsMessageRegExp = "Cannot determine head revision.*")
    public void testSubsequentChangeDetectionsWithCacheFailing(String branch, String previousChangeset, String srcRepo, List<String> expectedComments) throws Exception
    {
        testSubsequentChangeDetectionsWithCache(branch, previousChangeset, srcRepo, null, expectedComments);
    }

    @DataProvider
    Object[][] subsequentChangeDetectionsWithCacheData()
    {
        return new Object[][]{
                {"second", CHG_S_5, "master", CHG_M_4, asList(COMMENT_M_4, COMMENT_M_2)},
                {"master", CHG_M_4, "second", CHG_S_5, asList(COMMENT_S_5, COMMENT_S_3)},
        };
    }

    @Test(dataProvider = "subsequentChangeDetectionsWithCacheData")
    public void testChangeDetectionAfterSwitchingBranch(String prevBranch, String prevRev, String newBranch, String expectedRev, List<String> expectedComments) throws Exception
    {
        File repoSrc = new File(sourceRepositoriesBase, "5");

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, repoSrc, prevBranch);

        // feed the cache or the detached change won't be known
        BuildRepositoryChanges initialChanges = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", CHG_M_1); // initial build does not fetch to cache - maybe it should?
        Assert.assertEquals(initialChanges.getVcsRevisionKey(), prevRev);

        setRepositoryProperties(gitRepository, repoSrc, newBranch);
        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", prevRev);

        Assert.assertEquals(changes.getVcsRevisionKey(), expectedRev);

        List<String> comments = Lists.transform(changes.getChanges(), new ExtractComments());
        Assert.assertEquals(comments, expectedComments);

    }


    @DataProvider
    Object[][] crossSourceCheckoutData()
    {
        List<String[]> data = new ArrayList<String[]>();
        for (int src = 1; src <= 5; src++)
        {
            for (int dst = 1; dst <= 5; dst++)
            {
                data.add(new String[]{String.valueOf(src), "master", String.valueOf(dst), "master"});
                if (dst >= 3)
                {
                    data.add(new String[]{String.valueOf(src), "master", String.valueOf(dst), "second"});
                }
                if (src >= 3)
                {
                    data.add(new String[]{String.valueOf(src), "second", String.valueOf(dst), "master"});
                    if (dst >= 3)
                    {
                        data.add(new String[]{String.valueOf(src), "second", String.valueOf(dst), "second"});
                    }
                }
            }
        }
        return data.toArray(new Object[data.size()][]);
    }

    @Test(dataProvider = "crossSourceCheckoutData")
    public void testCrossSourceCheckout(String prevRepo, String prevBranch, String newRepo, String newBranch) throws Exception
    {
        GitRepository gitRepository = createGitRepository();

        File prev = new File(sourceRepositoriesBase, prevRepo);
        setRepositoryProperties(gitRepository, prev, prevBranch);

        String prevRev = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", null).getVcsRevisionKey();

        BuildContext buildContext = Mockito.mock(BuildContext.class);
        Mockito.when(buildContext.getPlanKey()).thenReturn("GIT-PLAN");

        File checkoutDir = new File(gitRepository.getWorkingDirectory(), "checkout");
        gitRepository.retrieveSourceCode(buildContext, prevRev, checkoutDir);

        File next = new File(sourceRepositoriesBase, newRepo);
        setRepositoryProperties(gitRepository, next, newBranch);

        String newRev = gitRepository.collectChangesSinceLastBuild("GIT-PLAN", null).getVcsRevisionKey();

        gitRepository.retrieveSourceCode(buildContext, newRev, checkoutDir);
        verifyContents(checkoutDir, "multiple-branches-with-conflicts-contents/" + newBranch + "-" + newRepo + ".zip");
    }

    @DataProvider
    Object[][] specificSourceCheckoutData()
    {
        return new String[][]{
                {"master", CHG_M_1, "1"},
                {"master", CHG_M_2, "2"},
                {"master", CHG_M_4, "4"},

                {"second", CHG_S_3, "3"},
                {"second", CHG_S_5, "5"},
        };
    }

    @Test(dataProvider = "specificSourceCheckoutData")
    public void testSpecificSourceCheckout(String branch, String revision, String contents) throws Exception
    {
        GitRepository gitRepository = createGitRepository();

        File prev = new File(sourceRepositoriesBase, "5");
        setRepositoryProperties(gitRepository, prev, branch);

        BuildContext buildContext = Mockito.mock(BuildContext.class);
        Mockito.when(buildContext.getPlanKey()).thenReturn("GIT-PLAN");

        gitRepository.retrieveSourceCode(buildContext, revision, getCheckoutDir(gitRepository));

        verifyContents(getCheckoutDir(gitRepository), "multiple-branches-with-conflicts-contents/" + branch + "-" + contents + ".zip");
    }
}
