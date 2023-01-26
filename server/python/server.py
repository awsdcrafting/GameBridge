from paho.mqtt import client as mqtt
from dotenv import load_dotenv
from dataclasses import dataclass, field, InitVar
from typing import Dict
import os
import json
import logging
import math


@dataclass
class Config():

    mqtt_server: str = ""
    mqtt_port: int = 1883
    mqtt_user: str = ""
    mqtt_pass: str = ""
    mqtt_global_control_topics: str = "gamebridge/control/"
    mqtt_client_topics: str = "gamebridge/game/"

    def __init__(self) -> None:
        self.mqtt_server = os.getenv("MQTT_SERVER", self.mqtt_server)
        self.mqtt_port = int(os.getenv("MQTT_PORT", self.mqtt_port))
        self.mqtt_user = os.getenv("MQTT_USER", self.mqtt_user)
        self.mqtt_pass = os.getenv("MQTT_PASS", self.mqtt_pass)
        self.mqtt_global_control_topics = os.getenv("MQTT_GLOBAL_CONTROL_TOPICS", self.mqtt_global_control_topics)
        self.mqtt_client_topics = os.getenv("MQTT_CLIENT_TOPICS", self.mqtt_client_topics)


@dataclass
class Chest():
    id: str
    whitelist: list[str] = field(default_factory=lambda: [])
    blacklist: list[str] = field(default_factory=lambda: [])
    items: Dict[str, float] = field(default_factory=lambda: {})

    def is_valid_item(self, item):
        if self.whitelist and item not in self.whitelist:
            return False
        if self.blacklist and item in self.blacklist:
            return False
        return True

    def add_items(self, items: Dict):
        ret_items = {}
        rejected_items = {}
        for item, amount in items.items():
            if not self.is_valid_item(item):
                rejected_items[item] = amount
            if item in self.items:
                self.items[item] = self.items[item] + amount
            else:
                self.items[item] = amount
            int_amount = math.floor(self.items[item])
            self.items[item] = self.items[item] - int_amount
            if int_amount > 0:
                ret_items[item] = int_amount
        return (ret_items, rejected_items)


@dataclass
class Game():
    sender: list[str]
    path: str
    topic_prefix: InitVar[str]
    chests: Dict[str, Chest] = field(default_factory=lambda: {}, hash=False, compare=False)
    topics: Dict[str, str] = field(default_factory=lambda: {}, hash=False, compare=False)

    def __post_init__(self, topic_prefix):
        self.topics["control_send"] = topic_prefix + "control/send/" + self.path
        self.topics["control_recieve"] = topic_prefix + "control/recieve/" + self.path
        self.topics["send"] = topic_prefix + "send/" + self.path
        self.topics["recieve"] = topic_prefix + "recieve/" + self.path

    def add_chest(self, chest: Chest):
        self.chests[chest.id] = chest
        logging.info("Added chest with id: {chest.id} to game {self.path}")

    def remove_chest(self, id):
        if id in self.chests:
            del self.chests[id]
        logging.info("Removed chest with id: {id} from game {self.path}")
        
    def add_items(self, id, items: Dict):
        if id not in self.chests:
            return {}, items
        return self.chests[id].add_items(items)
    
    def is_valid_item(self, id, item):
        if id not in self.chests:
            return False
        return self.chests[id].is_valid_item(item)


@dataclass
class Server():
    config: Config
    mqtt_client: mqtt.Client
    games: Dict[str, Game] = field(default_factory=lambda: {})
    conversion: Dict[str, Dict] = field(default_factory=lambda: {})

    def login(self, obj):
        sender = obj["sender"]
        path = "/".join(sender)
        self.games[path] = game = Game(sender, path, self.config.mqtt_client_topics)

        ret = {"target": sender, "topics": game.topics}
        logging.info(f"Connected game at {path}: {game}")
        #subsrcibe to the send topics
        self.mqtt_client.subscribe([(topic, 0) for topic in game.topics.keys() if "send" in topic])
        self.mqtt_client.publish(server.config.mqtt_global_control_topics + "recieve", json.dumps(ret))

    def logout(self, obj):
        sender = obj["sender"]
        path = "/".join(sender)
        if path in self.games:
            game = self.games.pop(path)
            #unsubscribe from the topics of this client
            self.mqtt_client.unsubscribe([topic for topic in game.topics.keys()])
        logging.info(f"Removed game at {path}")

    def get_game_by_topic(self, topic):
        if topic not in self.games:
            return None
        return self.games[topic]

    def add_chest(self, path, obj):
        game = self.get_game_by_topic(path)
        if not game:
            return

        if "chest_id" not in obj:
            logging.info("Invalid chest: [{path}]: {obj}")
            return
        chest_id = obj["chest_id"]
        whitelist = []
        if "whitelist" in obj:
            whitelist = obj["whitelist"]
        blacklist = []
        if "blacklist" in obj:
            blacklist = obj["blacklist"]
        chest = Chest(chest_id, whitelist, blacklist)
        game.add_chest(chest)
        logging.info(f"Added chest {chest_id} to {path}")

    def remove_chest(self, path, obj):
        game = self.get_game_by_topic(path)
        if not game:
            return

        if "chest_id" not in obj:
            logging.info("Invalid chest: [{path}]: {obj}")
            return
        chest_id = obj["chest_id"]
        game.remove_chest(chest_id)
        logging.info(f"Removed chest {chest_id} from {path}")
        
        
    def convert_item(self, item, origin: Game, target: Game):
        bases = [self.conversion]
        
        # traverse the origin path
        for path in origin.sender:
            if path not in bases[-1]:
                break
            bases += bases[-1][path]
        
        while bases:
            current_bases = [bases[-1]]
            
            # traverse the target path    
            for path in target.sender:
                if path not in current_bases[-1]:
                    break
                current_bases += current_bases[-1][path]
                
            while current_bases:
                #if conversion: return it
                if item in current_bases[-1]:
                    return current_bases[item]
                
                #go up one
                current_bases = current_bases[:-1]
            
            #if not found go up one
            bases = bases[:-1]
            
        #if not found return item
        return item
        
    def add_items(self, id, items, origin: Game):
        games = [game for game in self.games.values() if id in game.chests]
        items_per_game = {game: {} for game in games}
        
        for item, amount in items:
            valid_games = []
            for game in games:
                converted_item_info = self.convert_item(item, origin, game)
                if not game.is_valid_item(id, converted_item_info["output_name"]):
                    continue
                valid_games += [game]
                items_per_game[game][item] = {"amount": amount * converted_item_info["output_count"] / converted_item_info["input_count"], "item": converted_item_info["output_name"]}

            for game in valid_games:
                amount = items_per_game[game][item]["amount"] / len(valid_games)
                converted = items_per_game[game][item]["item"]
                del items_per_game[game][item]
                items_per_game[game][converted] = amount
            
        # send items
        for game, items in items_per_game.items():
            send_obj = {"sender": origin.sender, "target": game.sender, "reciever": id, "items": items}
            self.mqtt_client.publish(game.topics["recieve"], json.dumps(send_obj))


# The callback for when the client receives a CONNACK response from the server.


def on_connect(client, userdata, flags, rc):
    logging.info("Connected with result code " + str(rc))

    # Subscribing in on_connect() means that if we lose the connection and
    # reconnect then subscriptions will be renewed.
    # We subscribe to the global control send topic
    client.subscribe(server.config.mqtt_global_control_topics + "send")


# The callback for when a PUBLISH message is received from the server.


def on_message(client, server: Server, msg: mqtt.MQTTMessage):
    if msg.topic == os.getenv("MQTT_GLOBAL_CONTROL_TOPIC"):
        obj = json.loads(str(msg.payload))
        if "type" not in obj:
            #wrong object
            logging.info(f"Invalid data: [{msg.topic}]: {msg.payload}")
            return
        if obj["type"] == "login":
            server.login(obj)
        elif obj["type"] == "logout":
            server.logout(obj)

    elif msg.topic.startswith(server.config.mqtt_client_topics):
        sub_topic = msg.topic.removeprefix(server.config.mqtt_client_topics)
        if sub_topic.startswith("control/send/"):
            path = sub_topic.removeprefix("control/send")
            game = server.get_game_by_topic(path)
            if game == None:
                logging.warn(f"Unknown game: {path} with: [{msg.topic}]: {msg.payload}")
                #request game again, why are we even subscribed to this??
                return
            obj = json.loads(str(msg.payload))
            #(un)register chest
            if "type" not in obj:
                #wrong object
                logging.info(f"Invalid data: [{msg.topic}]: {msg.payload}")
                return
            if obj["type"] == "chest":
                server.add_chest(path, obj)
            elif obj["type"] == "remove_chest":
                server.remove_chest(path, obj)

        elif sub_topic.startswith("send/"):
            obj = json.loads(str(msg.payload))
            path = sub_topic.removeprefix("send/")
            game = server.get_game_by_topic(path)
            if not game:
                logging.warn(f"Recieved for unknown game: [{msg.topic}]: {msg.payload}")
                return
            if "reciever" not in obj:
                logging.info(f"Invalid send data: [{msg.topic}]: {msg.payload}")
            
            if "items" in obj:
                server.add_items(obj["reciever"], obj["items"], game)
            if "signals" in obj:
                # noop as of yet
                pass
        else:
            logging.debug(f"Unhandled game topic: [{msg.topic}]: {msg.payload}")

    else:
        # wrong topic
        logging.debug(f"Unhandled topic: [{msg.topic}]: {msg.payload}")


if __name__ == "__main__":
    load_dotenv()
    logging.basicConfig(format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

    client = mqtt.Client()
    server = Server(Config(), client)
    with open('item_conversions.json') as item_conversions_file:
        server.conversion = json.load(item_conversions_file)
    client.user_data_set(server)

    if server.config.mqtt_user:
        client.username_pw_set(server.config.mqtt_user, server.config.mqtt_pass)
    client.on_connect = on_connect
    client.on_message = on_message

    client.connect(server.config.mqtt_server, server.config.mqtt_port, 60)

    # Blocking call that processes network traffic, dispatches callbacks and
    # handles reconnecting.
    # Other loop*() functions are available that give a threaded interface and a
    # manual interface.
    client.loop_forever()
