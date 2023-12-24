import os
import json
import sys

input_path = "conversions.json"
output_path = "item_conversions.json"


def convert(from_game, to_game, reverse, conversions, output):
    default_input_count = 1
    default_output_count = 1
    print(conversions)
    if "default_conversion" in conversions:
        default_conversions = conversions["default_conversion"]
        print(default_conversions)
        if "item_input_count" in default_conversions:
            default_input_count = default_conversions["item_input_count"]
        if "item_output_count" in default_conversions:
            default_output_count = default_conversions["item_output_count"]


    if from_game not in output:
        output[from_game] = {}
    if to_game not in output[from_game]:
        output[from_game][to_game] = {}

    if reverse:
        if to_game not in output:
            output[to_game] = {}
        if from_game not in output[to_game]:
            output[to_game][from_game] = {}
    for item_name, conversion_info in conversions["items"].items():
        output[from_game][to_game][item_name] = {
                "input_count": conversion_info.get("input_count", default_input_count),
                "output_count": conversion_info.get("output_count", default_output_count),
                "output_name": conversion_info.get("output_name", item_name)
            }
        if reverse:
            output[to_game][from_game][conversion_info.get("output_name", item_name)] = {
                    "input_count": conversion_info.get("output_count", default_output_count),
                    "output_count": conversion_info.get("input_count", default_input_count),
                    "output_name": item_name
                }
    pass

if __name__ == "__main__":
    if not os.path.isfile(input_path):
        println("No input file!")
        sys.exit(1)
    if os.path.isfile(output_path):
        os.replace(output_path, output_path + ".back")

    #TODO mulitpass conversion (auto add a->c if a->b->c)

    full_conversions = {}
    base_conversions = {}
    with open(input_path) as input_file:
        base_conversions = json.load(input_file)

    for games, conversions in base_conversions.items():
        reverse = False
        if "<->" in games:
            game1, game2 = games.split("<->", 1)
            reverse = True
        else:
            game1, game2 = games.split("->", 1)
        convert(game1, game2, reverse, conversions, full_conversions)
        
    with open(output_path, "w") as output_file:
        json.dump(full_conversions, output_file, indent=4)