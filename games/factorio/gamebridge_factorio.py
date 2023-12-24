import factorio_rcon
import sys
import time
import json
import paho.mqtt.client as mqtt
import dotenv
import os

from dataclasses import dataclass, field

@dataclass
class Config():

    mqtt_server: str = ""
    mqtt_port: int = 1883
    mqtt_user: str = ""
    mqtt_pass: str = ""
    mqtt_global_control_topics: str = "gamebridge/control/"
    sender: list[str] = field(default_factory=list)
    
    mqtt_topics:dict = field(default_factory=dict)
    items:dict = field(default_factory=dict)
    
    def __init__(self) -> None:
        self.mqtt_server = os.getenv("MQTT_SERVER", self.mqtt_server)
        self.mqtt_port = int(os.getenv("MQTT_PORT", self.mqtt_port))
        self.mqtt_user = os.getenv("MQTT_USER", self.mqtt_user)
        self.mqtt_pass = os.getenv("MQTT_PASS", self.mqtt_pass)
        self.mqtt_global_control_topics = os.getenv("MQTT_GLOBAL_CONTROL_TOPICS", self.mqtt_global_control_topics)
        self.sender = ["factorio", "vanilla", "test"]
        self.mqtt_topics = {}
        self.items = {}

ip = "127.0.0.1"
port = 25566
password = "rconProd"

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
        config.items = {"items": json_dict["items"], "chest_id": json_dict["receiver"]}
        #TODO add items instead of replace
    else:
        print("Sth else")
    

def main():
    global config
    dotenv.load_dotenv()
    config = Config()
    
    client = factorio_rcon.RCONClient(ip, port, password)
    client.connect()


    mqttclient = mqtt.Client()
    mqttclient.on_connect = on_connect
    mqttclient.on_message = on_message
    mqttclient.user_data_set(config)


    mqttclient.username_pw_set(username=config.mqtt_user, password=config.mqtt_pass)

    mqttclient.connect(config.mqtt_server, config.mqtt_port, 60)

    mqttclient.subscribe(f"{config.mqtt_global_control_topics}receive")

    login = {
        "type": "login",
        "sender": config.sender,
    }
    mqttclient.publish(f"{config.mqtt_global_control_topics}send", json.dumps(login))
    
    mqttclient.loop_start()

    chests = set()
    while True:
        time.sleep(1)
        if not config.mqtt_topics:
            continue
        try:
            resp = client.send_command("/scis_gamebridge.get_chests")

            respdict = json.loads(resp)
            new_chests = set()
            for chest, data in respdict.items():
                print(f"{chest=} {data=}")
                if data["chest_type"] != "receiver":
                    continue
                new_chests.add(data["chest_id"])
                
            removed_chests = [chest for chest in chests if chest not in new_chests]
            added_chests = [chest for chest in new_chests if chest not in chests]
            for chest in removed_chests:
                mqttclient.publish(config.mqtt_topics["control_send"], json.dumps({"type": "remove_chest", "chest_id": chest, "sender": config.sender}))
            for chest in added_chests:
                mqttclient.publish(config.mqtt_topics["control_send"], json.dumps({"type": "chest", "chest_id": chest, "sender": config.sender}))
                
            chests = new_chests

            resp = client.send_command("/scis_gamebridge.get_items")
            respdict = json.loads(resp)
            for chest_id, items in respdict.items():
                if not items:
                    continue
                mqttclient.publish(config.mqtt_topics["send"], json.dumps({"type": "send", "sender": config.sender, "receiver": chest_id, "items": items}))

            if config.items:
                items = config.items
                config.items = {}
                print(f"Adding {items}: {json.dumps(items)}")
                resp = client.send_command("/scis_gamebridge.add_items " + json.dumps(items))
                respdict = json.loads(resp)
                print(f"added: {resp}")
            
            sys.stdout.flush()
        except Exception as ex:
            print(ex)
    return

def getPlayerCount():

    return
if __name__ == "__main__":
    main()