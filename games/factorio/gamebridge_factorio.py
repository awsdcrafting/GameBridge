import factorio_rcon
import sys
import time
import json
import paho.mqtt.client as mqtt
import dotenv
import os
import logging

from dataclasses import dataclass, field

@dataclass
class Config():
    rcon_server = "127.0.0.1"
    rcon_port = 25566
    rcon_password = ""
    mqtt_server: str = ""
    mqtt_port: int = 1883
    mqtt_user: str = ""
    mqtt_pass: str = ""
    mqtt_global_control_topics: str = "gamebridge/control/"
    sender: list[str] = field(default_factory=lambda: ["factorio", "vanilla", "test"])
    
    mqtt_topics:dict = field(default_factory=lambda: {})
    items:dict = field(default_factory=lambda: {})
    chests:set = field(default_factory=lambda: set())
    
    def __init__(self) -> None:
        self.rcon_ip = os.getenv("RCON_SERVER", self.rcon_server)
        self.rcon_port = int(os.getenv("RCON_PORT", self.rcon_port))
        self.rcon_password = os.getenv("RCON_USER", self.rcon_password)
        self.mqtt_server = os.getenv("MQTT_SERVER", self.mqtt_server)
        self.mqtt_port = int(os.getenv("MQTT_PORT", self.mqtt_port))
        self.mqtt_user = os.getenv("MQTT_USER", self.mqtt_user)
        self.mqtt_pass = os.getenv("MQTT_PASS", self.mqtt_pass)
        self.mqtt_global_control_topics = os.getenv("MQTT_GLOBAL_CONTROL_TOPICS", self.mqtt_global_control_topics)
        path = os.getenv("SENDER_PATH")
        if path:
            self.sender = path.split(".")
        else:
            self.sender = ["factorio", "vanilla", "test"]
        self.mqtt_topics = {}
        self.items = {}
        self.chests = set()



def on_connect(client, userdata, flags, rc):
    print("Connected with result code "+str(rc))

config = None
def on_message(client, config2: Config, msg):
    global config
    print(msg.topic+" "+str(msg.payload))
    json_dict = json.loads(msg.payload)
    m_type = json_dict["type"]
    target = json_dict["target"]
    print(f"{json_dict}")

    if m_type == "BACKEND_BOOT":
        config.chests = set()
        login = {
            "type": "login",
            "sender": config.sender,
        }
        
        client.unsubscribe(config.mqtt_topics["control_receive"])
        client.unsubscribe(config.mqtt_topics["receive"])
        client.publish(f"{config.mqtt_global_control_topics}send", json.dumps(login))
    
    if target != config.sender:
        print("wrong target")
        return

    if m_type == "topics":
        print("topics")
        config.mqtt_topics = json_dict["topics"]
        client.subscribe(config.mqtt_topics["control_receive"])
        client.subscribe(config.mqtt_topics["receive"])
    elif m_type == "send":
        print("send")
        chest_id = json_dict["receiver"]
        items = json_dict["items"]
        if chest_id not in config.items:
            config.items[chest_id] = {}
        for item, amount in items.items():
            pre_amount = config.items[chest_id].get(item, 0)
            config.items[chest_id][item] = amount + pre_amount
    else:
        print("Sth else")
    

def main():
    global config
    dotenv.load_dotenv()
    config = Config()
    
    client = factorio_rcon.RCONClient(config.rcon_server, config.rcon_port, config.rcon_password)

    mqttclient = mqtt.Client()
    mqttclient.on_connect = on_connect
    mqttclient.on_message = on_message
    mqttclient.user_data_set(config)
    mqttclient.will_set(f"{config.mqtt_global_control_topics}send", {"type": "logout", "sender": config.sender})

    mqttclient.username_pw_set(username=config.mqtt_user, password=config.mqtt_pass)

    mqttclient.connect(config.mqtt_server, config.mqtt_port, 60)

    mqttclient.subscribe(f"{config.mqtt_global_control_topics}receive")

    login = {
        "type": "login",
        "sender": config.sender,
    }
    mqttclient.publish(f"{config.mqtt_global_control_topics}send", json.dumps(login))
    
    mqttclient.loop_start()
    do_reconnect = True
    while True:
        time.sleep(1)
        if not config.mqtt_topics:
            continue
        try:
            if do_reconnect:
                client.connect()
                do_reconnect = False
            resp = client.send_command("/scis_gamebridge.get_chests")

            respdict = json.loads(resp)
            new_chests = set()
            for chest, data in respdict.items():
                logging.debug(f"{chest=} {data=}")
                if data["chest_type"] != "receiver":
                    continue
                new_chests.add(data["chest_id"])
                
            removed_chests = [chest for chest in config.chests if chest not in new_chests]
            added_chests = [chest for chest in new_chests if chest not in config.chests]
            for chest in removed_chests:
                mqttclient.publish(config.mqtt_topics["control_send"], json.dumps({"type": "remove_chest", "chest_id": chest, "sender": config.sender}))
            for chest in added_chests:
                mqttclient.publish(config.mqtt_topics["control_send"], json.dumps({"type": "chest", "chest_id": chest, "sender": config.sender}))
                
            config.chests = new_chests

            resp = client.send_command("/scis_gamebridge.get_items")
            respdict = json.loads(resp)
            for chest_id, items in respdict.items():
                if not items:
                    continue
                mqttclient.publish(config.mqtt_topics["send"], json.dumps({"type": "send", "sender": config.sender, "receiver": chest_id, "items": items}))

            if config.items:
                items = config.items
                config.items = {}
                logging.debug(f"Adding {items}")
                for chest, game_items in items.items():
                    json_items = {"items": game_items, "chest_id": chest}
                    resp = client.send_command("/scis_gamebridge.add_items " + json.dumps(json_items))
                    respdict = json.loads(resp)
                    logging.debug(f"added: {resp}")
            
            sys.stdout.flush()
        except factorio_rcon.RCONClosed as ex:
            logging.warning(ex)
            time.sleep(5)
            do_reconnect = True
        except factorio_rcon.RCONNotConnected as ex:
            logging.warning(ex)
            time.sleep(5)
            do_reconnect = True
        except factorio_rcon.RCONConnectError as ex:
            logging.warning(ex)
            time.sleep(10)
            do_reconnect = True
        except Exception as ex:
            logging.warning(ex)
            
    return

def getPlayerCount():

    return
if __name__ == "__main__":
    logging.basicConfig(format="%(asctime)s [%(levelname)s] %(name)s: %(message)s", level=logging.INFO)
    main()