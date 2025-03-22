import os
import sys
import yaml
from datetime import datetime

def log(message):
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")

def process_doc_file(input_file, output_file, sidebar_label, sidebar_position, title, description, tags, render_features):
    if not os.path.isfile(input_file):
        log(f"Error: Input file not found: {input_file}")
        return False

    os.makedirs(os.path.dirname(output_file), exist_ok=True)

    try:
        with open(input_file, 'r') as infile:
            content = infile.readlines()[1:]  # Skip the first line

        with open(output_file, 'w') as outfile:
            outfile.write("---\n")
            outfile.write(f"sidebar_label: {sidebar_label}\n")
            outfile.write(f"sidebar_position: {sidebar_position}\n")
            outfile.write(f"title: {title}\n")
            outfile.write(f"tags: {yaml.dump(tags, default_flow_style=True).strip()}\n")
            outfile.write(f"description: \"{description}\"\n")
            if render_features:
                outfile.write(f"render_features: '{render_features}'\n")
            outfile.write("---\n\n")
            outfile.writelines(content)

        return True
    except Exception as e:
        log(f"Error processing file {input_file}: {str(e)}")
        return False

def main(config_file):
    if not os.path.isfile(config_file):
        log(f"Error: Config file not found: {config_file}")
        sys.exit(1)

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    if 'files' not in config or not isinstance(config['files'], list):
        log("Error: 'files' key not found in config or is not a list")
        sys.exit(1)

    for index, entry in enumerate(config['files']):
        if entry is None:
            log(f"Error: Empty entry found at index {index} in config file")
            continue

        try:
            input_file = entry['input']
            output_file = entry['output']
            sidebar_label = entry['sidebar_label']
            sidebar_position = entry['sidebar_position']
            title = entry['title']
            description = entry['description']
            tags = entry['tags']
            render_features = entry.get('render_features', None)
        except KeyError as e:
            log(f"Error: Missing required key {e} in entry at index {index}")
            continue

        log(f"Processing: {input_file} -> {output_file}")
        if process_doc_file(input_file, output_file, sidebar_label, sidebar_position, title, description, tags, render_features):
            log(f"Successfully processed: {input_file} -> {output_file}")
        else:
            log(f"Failed to process: {input_file}")

    log("Documentation processing complete.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python process_docs.py <config_file>")
        sys.exit(1)
    
    main(sys.argv[1])