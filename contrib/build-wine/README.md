Windows Binary Builds
=====================

✓ _This build is reproducible: you should be able to generate
   binaries that match the official releases (i.e. with the same sha256 hash)._

In order to build for Windows, you must use docker.
Don't worry! It's fast and produces 100% reproducible builds.
You may do so by issuing the following command (from the top-level of this
repository)::

    $ contrib/build-wine/build.sh BRACH_OR_TAG

Where BRANCH_OR_TAG above is a git branch or tag you wish to build.

Note: If on a Linux host, the above script may ask you for your password as
docker requires commands be run as either `root` or a user in the `docker` group.
Make sure you are in the `docker` group (edit `/etc/groups`, log out, log back in).
On a macOS host, this is not the case and docker can be run as a normal user.

The built .exe files will be placed in: `dist/`
