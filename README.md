# Basheetka

Basheetka is a standalone [babashka](https://github.com/babashka/babashka) script which turns a `babashka` `bb.edn` config file into a "spreadsheet".

Similar to a `Makefile` or `Justfile`, babashka's `bb.edn` configuration file can contain [tasks](https://book.babashka.org/#tasks). When run by the babashka task runner, tasks are evaluated to values, and importantly, tasks can depend on other tasks; this is similar to a spreadsheet containing cells which are evaluated to values, and these cells in turn can depend on other cells.

## Installation

Ensure `babashka` is [installed](https://github.com/babashka/babashka#installation). Then simply pull down a copy of the `basheetka.bb` script from Github:

    $ curl -O https://github.com/safehammad/basheetka/blob/main/basheetka.bb

Alternatively, you can clone the entire repo which, in addition to `basheetka.bb` will contain other files such as a test script and this documentation:

    $ git clone https://github.com/safehammad/basheetka.git

## Usage

### 1. Create an initial `bb.edn` file:

    $ bb basheetka.bb init

Beware that any existing `bb.edn` file will be overwritten. This is a barebones file, an "empty spreadsheet" with some helper tasks referring to functions in `basheetka.bb`.

### 2. Import spreadsheet:

    $ bb import sheet.csv

The spreadsheet format recognised is CSV. Formulae can be supplied as Clojure S-expressions. For example:

       | A |     B     |
    ---|---|-----------|
     1 | 5 |     6     |
     2 | 4 | (+ A1 A2) |

Note that rows and columns (rows 1/2, cols A/B) have been displayed here for descriptive purposes. They will usually be shown in your spreadsheet application such as LibreOffice or Microsoft Excel but don't form part of the CSV file. Importing the spreadsheet will directly update the `bb.edn` file with new tasks, each corresponding to a cell in the spreadsheet.

### 3. Query the resulting `bb.edn` file:

For the example spreadsheet above, the `bb.edn` file will look something like this:

    {:paths ["."],
     :tasks
     {:requires ([basheetka :as bs]),
      :leave (bs/leave),
      import {:task (bs/import)},
      export {:task (bs/export), :depends [A1 B1 A2 B2]},
      A1 {:task 5},
      B1 {:task 6},
      A2 {:task 4},
      B2 {:task (+ A1 A2), :depends [A1 A2]}}}

The evaluated value of tasks can be printed out as follows:

    $ bb --prn A1
    5

    $ bb --prn B2
    9

In this example, the value of cells `A1` and `B1` are displayed. Note that the formula `(+ A1 A2)` in cell `B2` has been evaluated.

### 4. Export spreadsheet:

    $ bb export evaluated-sheet.csv

Rather than querying task by task, the entire evaluated spreadsheet can be exported. The resulting spreadsheet in our example will look like this:

       | A | B |
    ---|---|---|
     1 | 5 | 6 |
     2 | 4 | 9 |

## Tests

Run the tests as follows:

    $ bb basheetka_test.bb

## Todo

- Ask before overwriting an existing bb.edn file and provide a `--force` option to overwrite without asking.
- Specify a config file other than `bb.edn` through a `--config` option. This is similar to the `--config` option available in `bb` (run `bb help` for more detail).

## Feature ideas

- An option to generate a standalone `bb.edn` file without any reference to `basheetka.bb`.  There are two options:
  - The `bb.edn` file remains "pure" containing only spreadsheet cells as tasks which can be resolved using `bb --prn [cell]`. All reference to import/export tasks is removed.
  - The import/export functionality which relies on basheetka.clj is completely embedded within the generated bb.edn.
- Accept Excel style formulas in addition to S-expressions. For example, `(+ A1 B1)` could also be written as `=A1+B1`. Note that this would also enable simple references to other cells, for example, `=A1`.

## Other options

If you're looking for a mature library option for managing data with dependencies you might like to consider [Pathom 3](https://github.com/wilkerlucio/pathom3).

## History

Basheetka was initially created as a bit of fun, a challenge, and to prove a point. After reading [this paper](https://www.microsoft.com/en-us/research/uploads/prod/2020/04/build-systems-jfp.pdf) which talks about Excel as a build system, I wondered if in reverse, a `Makefile` could be used as a spreadsheet. I signed up to go to the first `babashka-conf` in Berlin on 10th June 2023 realising that `babashka` would be a great basis for this compared to a `Makefile` and set about creating `basheetka` just in time to give a [lightning talk](https://www.youtube.com/watch?v=qMcM4Wi1BNA) about it at the conference.

## License

Copyright © 2023 Safe Hammad

Distributed under the EPL License. See LICENSE.
