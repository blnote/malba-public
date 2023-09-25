# MALBA
## Overview
This is an implementation of the MALBA algorithm for bibliometric networks. MALBA stands for Multilayer Adjustable Local Bibliometric Algorithm. It constructs cohesive communities in networks of publications by iteratively growing a subgraph from a seed. For more information see our [Paper](https://dapp.orvium.io/deposits/64ad562c170ba03b15d89b4e/view).

## Run
- make sure Java Runtime Engine is installed (Java 11 or later)
- download and unzip [release](https://github.com/blnote/malba-public/releases) to a directory of your choice
- download Gephi Toolkit version 0.10.0 from [here](https://github.com/gephi/gephi-toolkit/releases/download/v0.10.0/gephi-toolkit-0.10.0-all.jar) and place in directory
- within this directory run `java -jar malba-X.jar`, where X is the release version
- the program comes with two configuration files: `malba-algo.edn`  and `database.edn` to configure the algorithm parameters and database settings

## License
The source code in this repository is licensed under the MIT License. It is written in [Clojure](https://www.clojure.org) using the [Gephi-Toolkit](https://github.com/gephi/gephi/wiki/Toolkit) for graph visualization. However, the Gephi-Toolkit itself and its third-party components are under different licenses, see [here](https://gephi.org/developers/license/). By downloading and using the provided binary you are agreeing to these respective licenses. See LICENSE.md for detailed information.

## Compiling from source code
You need
 - Java JDK 11 (or later)
 - Clojure [installation instructions](https://clojure.org/guides/install_clojure)
 - source code (click on Releases)
 - Gephi Toolkit version 0.10.0, can be downloaded from [here](https://github.com/gephi/gephi-toolkit/releases/download/v0.10.0/gephi-toolkit-0.10.0-all.jar)

 1. save the downloaded Gephi Toolkit jar in the root directory of the source code
 2. within the root directory execute `clojure -T:build uber`
 3. the root directory should contain a zip file malba-Version.zip containing a jar file and configuration files.
 4. proceed as described in Section Run above.