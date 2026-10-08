#!/usr/bin/env bash
set -euo pipefail
# Export the repaired sheet without replacing antialias alpha with a binary mask.
# v36/v37 promoted almost-transparent stray pixels to fully opaque pixels.
sheet_path="$1"
output_path="$2"
mkdir -p "$output_path"
convert "$sheet_path" -crop 427x253+432+231 +repage "$output_path/cat_rig_body.png"
convert "$sheet_path" -crop 307x356+913+112 +repage "$output_path/cat_rig_tail.png"
convert "$sheet_path" -crop 217x275+120+593 +repage "$output_path/cat_rig_front_near.png"
convert "$sheet_path" -crop 162x262+578+604 +repage "$output_path/cat_rig_front_far.png"
convert "$sheet_path" -crop 210x289+994+583 +repage "$output_path/cat_rig_rear_near.png"
convert "$sheet_path" -crop 212x276+115+931 +repage "$output_path/cat_rig_rear_far.png"
if [ "$#" -ge 3 ]; then
    # Optional separately repaired torso: normalize its authoring dimensions.
    convert "$3" -resize '427x253!' "$output_path/cat_rig_body.png"
fi
