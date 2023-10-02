# MALBA
## Overview
This is an implementation of the MALBA algorithm for bibliometric networks. MALBA stands for Multilayer Adjustable Local Bibliometric Algorithm. It constructs cohesive communities in networks of publications by iteratively growing a subgraph from a seed. For more information see our [Paper](https://dapp.orvium.io/deposits/64ad562c170ba03b15d89b4e/view). 

Currently the database mode of MALBA only works with the non-public bibliometric database provided by the German “Competence Network for Bibliometrics”, which consists of Web of Science data. However, the implementation is written in a way that should make it straightforward to connect to different databases and we plan to also use open databases in the future.

![Sample Screenshot](/doc/screenshot.png "Screenshot")

## Run
- make sure Java Runtime Engine is installed (Java 11 or later)
- download and unzip [release](https://github.com/blnote/malba-public/releases) to a directory of your choice
- download Gephi Toolkit version 0.10.0 from [here](https://github.com/gephi/gephi-toolkit/releases/download/v0.10.0/gephi-toolkit-0.10.0-all.jar) and place in directory
- within this directory run `java -jar malba-X.jar`, where X is the release version

## Usage
The program comes with two configuration files: `malba-algo.edn`  and `database.edn` to configure algorithm parameters and database settings. In general the program should work using the default settings. For convenience, your database credentials for the German “Competence Network for Bibliometrics” can be put in `database.edn` to avoid entering them repeatedly. Note that the database is evolving and may change its relation names. In this case the SQL statements in `database.edn` have to be adjusted.


### Loading seeds and network
To start working with MALBA you need to load a seed, which is a local text file containing one publication id per line. Currently publication ids are of the format `WOS:000000000000000`.
Next you need to decide if you want to work with a database or a local citation network. 
- in case of a database, press on `from database`, enter your database credentials and click on connect. When connected successfully MALBA starts looking up the seeds in the database, downloads all relevant citation information and displays the seed as a directed graph.
- in file mode you need a text file containing two publications ids per line. Citation information is then generated by assuming the first publication of each line cites the second. Press on `from file` to choose and load the corresponding file.
### Running the algorithm
When seed and network have been loaded successfully the algorithm initializes and the part of the network consisting of the seed publications is displayed. You can now control the algorithm via
- `Step:` runs one step of the algorithm (parameters can be varied in each step). A step of the algorithm consists of three parts (DCout DCin and BC). Each part can be disabled by unchecking the respective box.
- `Run:` runs the algorithm using the current subgraph as seed until no new nodes are added to the subgraph or the subgraph is grew too large (`max graph size` parameter).
- `Search:` starts with the current subgraph as seed and searches through the parameter space to find the largest subgraph containing it. This step may take a while. Note that you can interrupt the search at any time. The parameters will be updated to the ones generating the largest subgraph that has been found until interruption. Pressing `Run` then generates this subgraph.
- `Reset:` resets the subgraph to the original seed.

#### MALBA parameters (see publication for details):
There are three parameters controlling the inclusion of a candidate publication. A publication is added to the current subgraph if
- the subgraph cites it at least `DCout` times (an integer)
- the ratio of its citations that are in the subgraph is at least `DCin` (0.0-1.0) 
- the ratio of its citations that are *citations* of the subgraph is at least `BC` (0.0-1.0)

Further there are two parameters limiting subgraph growth
- `max. graph size`: the maximal size of the subgraph
- `max. parents`: to avoid an explosion in the number of candidate publications we only include the "parents" (citing publications) of candidates that are cited at most `max. parents` times (e.g. 1000). Note that  parents excluded this way may still end up in the candidate set if they cite another less cited candidate. 

### Visualizing results
The current subgraph is always displayed on the right hand side. You can
- `Zoom` using mouse wheel and drag the Graph around by keeping the left mouse button pressed
- `Layout` the graph using various algorithms by pressing the respective button below the graph. Layouts can be mixed or applied repeatedly to improve results. 
- display surrounding publications of the current subgraph by pressing `Show Surroundings`. These are publications that are candidates for the subgraph but, due to algorithm parameters, have not been included (yet).
 
- `Hover` the mouse over a publication to display details (only in database mode). Hovering enlarges a publication node and its neighbors to give a better view of its connections.
- `Right click` on a publication to copy detail information to clip board
- `Left click` on a publication to only view it together with its neighbors (click on `Reset View` to show the whole graph again)


### Exporting results

Results can be exported as `csv`, `pdf` or as `gephi` file through `Export` in the main menu. Note that only the current visible graph will be exported, making it e.g. possible to export only the neighbors of a selected publication).

Further it is possible to save a session including the current parameters and the cached database results to possibly speed up future applications (citation information from the database is cached as long as memory is available).

    

## License
The source code in this repository is licensed under the MIT License. It is written in [Clojure](https://www.clojure.org) using the [Gephi-Toolkit](https://github.com/gephi/gephi/wiki/Toolkit) for graph visualization. However, the Gephi-Toolkit itself and its third-party components are under different licenses, see [here](https://gephi.org/developers/license/). By downloading and using the provided binary you are agreeing to these respective licenses. See LICENSE.md for detailed information.

## Compiling from source code
You need
 - Java JDK 11 (or later)
 - Clojure [installation instructions](https://clojure.org/guides/install_clojure)
 - MALBA source code ([releases](https://github.com/blnote/malba-public/releases))
 - Gephi Toolkit version 0.10.0, can be downloaded from [here](https://github.com/gephi/gephi-toolkit/releases/download/v0.10.0/gephi-toolkit-0.10.0-all.jar)
 
 1. unzip MALBA source code
 2. save the downloaded Gephi Toolkit jar in the root directory of the source code
 3. within the root directory execute `clojure -T:build uber`
 4. the root directory should contain a zip file malba-Version.zip containing a jar file and configuration files.
 5. proceed as described in Section Run above.