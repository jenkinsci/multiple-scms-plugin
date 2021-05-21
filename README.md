# Jenkins Multiple SCMs Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/multiple-scms.svg)](https://plugins.jenkins.io/multiple-scms)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/multiple-scms-plugin.svg?label=release)](https://github.com/jenkinsci/multiple-scms-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/multiple-scms.svg?color=blue)](https://plugins.jenkins.io/multiple-scms)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins%2Fmultiple-scms-plugin%2Fmaster)](https://ci.jenkins.io/job/Plugins/job/multiple-scms-plugin/job/master/)
[![GitHub license](https://img.shields.io/github/license/jenkinsci/multiple-scms-plugin.svg)](https://github.com/jenkinsci/multiple-scms-plugin/blob/master/LICENSE)

    Deprecated: Users should migrate from "legacy" job definitions to
    https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Plugin
    which generally offers a better way of checking out from multiple
    SCMs, and is supported by the Jenkins core development team.

This plugin allows a Jenkins job to check out sources from multiple SCM
providers. It is useful for legacy job types (e.g. freestyle) which need
to construct a workspace from several unrelated repositories, but does
not intend to support Pipeline jobs and is not needed there (a series of
`checkout` steps with an argument or a wrapping `dir` step for dedicated
checkout directory can fulfill that role).

Development of this plugin is essentially frozen, and its ability to do
work may have relied on capabilities of various SCM and Branch Source
plugins, some of which are known to have obsoleted or removed the code
for those capabilities.

## Building

This plugin requires gradle in order to build.  See:
https://github.com/jenkinsci/gradle-jpi-plugin/#usage

If you have gradle version 6 or newer installed locally, you can just
build with `gradle jpi`. For Ubuntu/Debian like systems, current distros do
not provide a recent enough version of the tool, but an alternate PPA works:
https://computingforgeeks.com/install-gradle-on-ubuntu-debian-from-ppa/

One of the ways to do a local build in docker (e.g. if preparing a PR) is:

````
:; git clone git@github.com:jenkinsci/multiple-scms-plugin.git
:; cd multiple-scms-plugin
:; docker run --rm -u root -v "$PWD":/home/gradle/project -w /home/gradle/project gradle gradle build
````

## Background, per original author

This plugin is more of a proof-of-concept than a robust and fully
functional component. It does work in my particular build environment,
and is meant to serve as a demonstration of what might be possible with
more work. It was inspired by
[JENKINS-7155](https://issues.jenkins-ci.org/browse/JENKINS-7155)
requesting multiple repository checkouts for Mercurial similar to what
is possible with the Subversion plugin. It's currently implemented as a
plugin, but if enough people find it useful, I think the idea would work
better in the Jenkins core.

We are a small team and have been using Subversion which has been
adequate for our needs. However, I wanted to experiment with distributed
VCS systems, so I tried using Mercurial for a medium-sided update to one
of our projects and enjoyed it very much. Everything went great until I
got to the Jenkins build part. Our projects are small to mid-sized and
are usually structured like:

    /
      /project-code
      /inhouse-common-library
      /3rd-party-deps
         /lib1
         /lib2

The in-house library is built by an upstream job, but the 3rd-party
dependencies are checked out as part of the build. With subversion, we
could check each one out individually, and initially I thought this is
what I wanted for Mercurial as well. However, quite often we have the
entire source for the library checked in, but the build really only
needs a single dll or jar. Since Mercurial doesn't support partial
clones, it now seems better to leave these in subversion since they're
rarely changed and we can check out just the parts needed during a
build.

I googled for solutions, but the ones I found all seemed to have
limitations compared to the working and adequate subversion solution:

1.  Script the dependency checkouts as part of the build. This would
    work and would be easy, but changes to third party libraries would
    not automatically trigger a build or be included in the change log.
2.  Have an upstream job to check out the dependencies. This could work,
    but it sounded tedious to set up and maintain, and would clutter the
    job list with lots of superfluous jobs.
3.  Mercurial forests/subrepos. The general feel I got was that the
    forest extension is not actively supported, and subrepos are not yet
    supported by the Mercurial plugin. Furthermore, this would require
    changes to how we structure our code. If we were a bigger shop, this
    would probably be worthwhile, but for me it seemed like more
    maintenance.
4.  Just leave everything in subversion and live with it. The easiest of
    all, but loses all the benefits of DVCS (however minimal for a team
    our size).

## Usage

In the SCM section of the Jenkins job configuration screen, choose
'Multiple SCMs'. You'll then see a drop-down list of the available SCM
plugins which can be configured similar to the way build steps are
configured for a freestyle job. When chosen, each SCM plugin will
present its normal set of configuration options.

Make sure each SCM checkout is done in a separate sub-directory of the
workspace to avoid conflicts between the SCM providers. If using
Mercurial, this option is hidden in the 'Advanced' section, so don't
forget to configure it.

If changing the SCM provider for an existing job, I recommend wiping out
the workspace.

## Limitations

-   Currently tested only with Mercurial and Subversion plugins, as that
    is what I use locally. Many other users succeeded with Git (GitHub,
    BitBucket) SCM sources as well.
-   Post-commit type triggers don't currently work (at least for
    subversion), so it is necessary to configure 'cron' type polling.
-   Repository browser configuration is also not supported in the
    current version.
-   I haven't tested any pre/post build tagging type operations,
    although they will probably work.
-   I also haven't tested master/slave configurations. This might work
    if the underlying SCM plugins support this mode of operation.

## Implementation Notes

The implementation was easier than I originally expected, and learned a
lot along the way. It basically serves as a proxy between Jenkins and
existing SCM plugins. The job configuration panel uses a hetero-list
similar to the build steps section. That way, all the configuration
options are handled by the real SCM plugins. For the actual SCM
functions, it pretty much just iterates over each configured SCM plugin
and forwards the request. There is some messiness in dealing with
changelogs, but it's not too bad.

## Changelog

### Version 0.9 (unreleased)

-   TBD...

### Version 0.8 (May 21, 2021)

-   Escape possible XML in SCM keys when logging
-   Various fixes to build-related files and notes

### Version 0.7 (May 21, 2021)

-   Update to modern Jenkins GUI elements (table-to-div migration) which
    requires a Jenkins core 2.264 or newer
-   Various build system modernization to be able to publish the release
-   Documentation imported into plugin source from the obsoleted Wiki

### Version 0.6 (Apr 3, 2016)

-   Fix SCM polling
    ([JENKINS-25253](https://issues.jenkins-ci.org/browse/JENKINS-25253))
-   Export environment variables from each SCM, instead of using only
    the variables from the last SCM
    ([JENKINS-30709](https://issues.jenkins-ci.org/browse/JENKINS-30709))
-   Fix repository browser

### Version 0.5 (Jul 13, 2015)

-   Use newInstance()
    ([JENKINS-9287](https://issues.jenkins-ci.org/browse/JENKINS-9287) ,
    [JENKINS-19818](https://issues.jenkins-ci.org/browse/JENKINS-19818))
-   Fix exceptions
    ([JENKINS-27638](https://issues.jenkins-ci.org/browse/JENKINS-27638))

### Version 0.4 (Mar 9, 2015)

-   Fix usage with Subversion plugin version 2.5
    ([JENKINS-26303](https://issues.jenkins-ci.org/browse/JENKINS-26303))
-   Fix ChangeLog across multiple git repositories
    ([JENKINS-25131](https://issues.jenkins-ci.org/browse/JENKINS-25131))

### Version 0.4-beta-1 (Jun 16, 2014)

-   Support for updated SCM API in Jenkins 1.568+.

### Version 0.3

Changes not recorded.

### Version 0.2 (Jan 19, 2012)

-   Fix ChangeLog parsing for subversion (and possibly others).
    Extracted log component had an extra newline at the top of the file
    which made parsing fail if the document contained an `<?xml...>`
    declaration.
-   [JENKINS-7155](https://issues.jenkins-ci.org/browse/JENKINS-7155),
    [JENKINS-12298](https://issues.jenkins-ci.org/browse/JENKINS-12298)
    Allow polling to work with multiple instances of a single SCM type
    (Thanks to Jesse Glick).
-   Implement `getKind()` to possibly allow other clients (such as
    NetBeans IDE) to parse the change logs (Thanks to Jesse Glick).
-   Add override of `getModuleRoots()` to return the union of all
    contained SCMs module roots.
-   For SCM implementations that add `SCMRevisionState` actions to the
    build, these are now correctly recorded in the build, so subsequent
    polling works correctly.
-   Forward requests for build environment variables to the contained
    SCMs. Fixes missing `MERCURIAL_REVISION` and possibly others
    depending on SCM plugins used.

### Version 0.1 (Mar 08, 2011)

-   Initial release
