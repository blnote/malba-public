# MALBA
## Overview
This is a java program implementing the MALBA algorithm for bibliometric networks. MALBA stands for Multilayer Adjustable Local Bibliometric Algorithm. It constructs cohesive communities in networks of publications by iteratively growing a subgraph from a seed. For more information see [Paper](https://link-to-paper). TODO: include screenshot, example?

## License
The source code in this repository is licensed under the MIT License (see LICENSE.md file). It is written in [Clojure](https://www.clojure.org) using the [Gephi-Toolkit](https://github.com/gephi/gephi/wiki/Toolkit) for graph visualization. However, the Gephi-Toolkit itself and its third-party components are under different licenses, see [here](https://gephi.org/developers/license/). By downloading and using the provided binary you are agreeing to these respective licenses.

## Run
- 

## Compiling from source code
You need
 - Java JDK 11 (or later)
 - Clojure [installation instructions](https://clojure.org/guides/install_clojure)
 - source code (click on Releases)
 - Gephi Toolkit version 0.10.0, can be downloaded from [here](https://github.com/gephi/gephi-toolkit/releases/download/v0.10.0/gephi-toolkit-0.10.0-all.jar)

 1. save the downloaded Gephi Toolkit jar in the root directory of the source code
 2. within the root directory execute `clj -T:build uber`
 3. the `target` subdirectory should contain a jar file that can be run using `java -jar malba-X.jar` where X is the current version. 
