To run all tests:
1. place test data consisting of `seedWOS.txt` and `networkWOS.txt` and `database-local.edn` in `./test/data` directory
2. start testrunner from command line with `clj -M:test` . To only run an integration test use `clj -M:test -n malba.integration-test`. Afterwards an exported test session and graphs can be found in `./test/data/exports` 