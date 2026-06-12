# DevPortal documentation automatic synchronization

This document explain the pipeline and process that was configured to set up 
the process to automatically update the Devportal documentation related with core team
from the `docs` folder repository documentation. This allows Core team to have a single
source of documentation, while maintaining an up to date version on the DevPortal.

## Pre-requisites
The automation requires the core repo to have configured a Github secret with a Personal Access Token (PAT) with the 
necessary permissions to update the DevPortal repository. The PAT should be stored in the core repository as a Github 
secret named `DEVPORTAL_DOCS_UPDATE_TOKEN`. If you don't have access, be sure to talk with security 
to do so.

1. **Access to Repositories**:
   1. Write access to the target repository (https://github.com/rsksmart/devportal)
   2. Permissions to create and manage Github secrets in the project repository. This is handled by Security.
2. **Github Personal Access Token (PAT)**:
   1. Note: This is handled by Security. At the moment we are using a temporary PAT that will be replaced by a new one, so there is no need to create a new one.
   2. Fine-grained PAT settings:
      1. name: DEVPORTAL_DOCS_UPDATE_TOKEN
      2. **Organization**: `rsksmart` 
      3. **Repository access**: only `rsksmart/devportal`
      4. **Organization permissions**: none
      5. **Repository permissions**:
         1. Metadata: read-only
         2. Commit statuses: read and write
         3. Contents: read and write
         4. Pull requests: read and write
   3. Store the PAT securely in the project repository as a Github Secret as `DEVPORTAL_DOCS_UPDATE_TOKEN`

## Merged DevPortal and Core Documentation

The DevPortal and Core documentation must be aligned for the implementation of the automation to work.

The DevPortal renders the different sections / pages from markdown files, which are located in 
a directory structure inside the docs directory of the repository. Each section page is a markdown file,
and a project can have one or multiple files, depending on the complexity of the project documentation.

Also, each markdown file on the DevPortal has a `frontmatter` on the top section of the file, which specifies metadata and
options on how that page has to be rendered when the DevPortal is built. But that `frontmatter` is needed
only for the DevPortal markdown files, and not for the documentation files included in the project repository,
so that will be the main difference between both files.

The whole automation process it's basically replicate the file structure we have in devportal and we want to keep
synched with the one we have here. For example, we have the folder `03-node-operators` with the structure:

```
├── 03-node-operators
│   ├── 04-setup
│   │   ├── 02-installation
│   │   │   ├── docker.md
│   │   │   ├── index.md
│   │   │   ├── java.md
│   │   │   └── ubuntu.md
│   │   ├── 03-configuration
│   │   │   ├── 02-cli.md
│   │   │   ├── 03-reference.md
│   │   │   ├── 04-switch-nework.md
...
```

The idea is that any file changed under the `03-node-operators` folder will be automatically updated in the DevPortal
[repository](https://github.com/rsksmart/devportal/tree/main/docs/03-node-operators/04-setup) in a Pull Request (PR) created by the pipeline configured `devportal-update.yml` in the core repository, as you can see
an example [here](https://github.com/rsksmart/devportal/pull/291).  

Once the devportal-update pipeline runs, you can go to Actions and check under a successfully run, that it was created a PR in devportal 
in the step `Create Pull Request` from the pipeline. You can also check the logs of the pipeline to see the details of the PR created.
[Update DevPortal Documentation](https://github.com/rsksmart/rskj/actions/workflows/devportal-update.yml).

## Pipeline Configuration

The pipeline is configured in the core repository, and it's triggered when a change is detected in the `docs` folder.
Once there is a change there, it does the following:

1. Clone the `devportal` repo locally in the runner.
2. Create a local branch to hold the changes with the current version from `rskj`
3. Call the script [`process_docs.py`](../.github/workflows/scripts/devportal-update/process_docs.py)
passing the [`doc_config.yaml`](../.github/workflows/scripts/devportal-update/doc_config.yaml) 
to update the documentation in the local branch from `devportal` with all the changes.
4. Commit the changes in the local branch.
5. Creates a PR in the devportal repository with the changes.

### Process docs python script

Let's break it down the python script explaining what it does. The script is split in two important parts,
the `main` function and the `process_doc_file` function.

1. **main**:  Process files based on the configuration file which path is passed as argument. 
This function reads a YAML configuration file, processes each file specified in the configuration,
and applies the necessary formatting and metadata.
2. **process_doc_file**: This function process a documentation file by adding metadata and formatting.
It reads the input file, adds metadata (such as sidebar information, title, tags, and description), and 
writes the processed content to an output file.

### Configuration file - yaml

This file contains the list of all files that should be processed and synced against the `devportal` 
repository. Each file should have the following parameters:

- **input_file** (str): Path to the input documentation file.
- **output_file** (str): Path where the processed file will be saved.
- **sidebar_label** (str): Label to be used in the sidebar for this document.
- **sidebar_position** (int): Position of this document in the sidebar.
- **title** (str): Title of the document.
- **description** (str): Brief description of the document.
- **tags** (list): List of tags associated with the document, they should be between squared
brackets and separated by comma. Ex.: `[hardware, specs, requirements]`
- **render_features** (str, optional): Rendering features to be applied to the document.

Once a new file is added to be synced, we should add it to this list. It's worth mentioning that
this configuration file should contain the `metadata` configuration to be added in the header
of each doc file. This `metadata` is the `frontmatter` that the DevPortal uses to render the file
and should be gotten from the `devportal` source repository.