# GameBridge

A system designed for multiple games to work together

e.g. Producing iron in minecraft and process it in factorio to steel

## documentation

comming soon™

## conversion

The specialized converters have priority over the more general

A conversion will happen in this order:

-   origin_type->origin_name->origin_id
-   origin_type->origin_name->default
-   origin_type->default
-   default

A conversion can have on of 4 results:

-   converted
    a successful conversion happend
-   anyway

    we use the result anyway (mostly used for sending items / the specialized converters)

-   reject

    reject the input item

    cases:

    -   sending

        return it to the sending chest

    -   recieving

        send a reject notification to the origin

-   delete

    delete the input item

## Game connected

logs in at global control topic with its identification (sender path)

```json
{
    "sender": ["factorio", "vanilla", "1"],
    "type": "login"
}
```

server responds with 4 topics

```json
{
    "target": ["factorio", "vanilla", "1"],
    "topics": {
        "control_send": "gamebridge/game/control/send/...",
        "control_recieve": "gamebridge/game/control/recieve/...",
        "send": "gamebridge/game/send/...",
        "recieve": "gamebridge/game/recieve/..."
    }
}
```

## Game disconnected

logs out at global control topic with its identification (sender path)

## New Chest / Chest updated

game sends json to control topic with following infos

-   sender path
-   type = chest
-   chest id
-   item white/black list
-   nonce

```json
{
    "sender": ["factorio", "vanilla", "1"],
    "type": "chest",
    "chest_id": "1",
    "whitelist": [],
    "blacklist": [],
    "nonce": ""
}
```

server responds with ack?

## Remove Chest

game sends json to control topic with following infos

-   sender path
-   type = remove_chest
-   chest id

```json
{
    "sender": ["factorio", "vanilla", "1"],
    "type": "remove_chest",
    "chest_id": "1"
}
```

## Sending things

game sends json to send topic  
items and data can be send  
both fields are optional  
data is up to the reciever to interpret if at all  
items is item_id: amount

```json
{
    "sender": ["factorio", "vanilla", "1"],
    "reciever": "id of reciever chest",
    "items": {
        "iron-plate": 100,
        "copper-plate": 50
    },
    "data": {}
}
```

## Recieving things
server sends json to recieve topic
items and data can be send
both fields are optional
data will just be copied over
items are converted

example minecraft is on the recieving end
```json
{
    "sender": ["factorio", "vanilla", "1"],
    "target": ["minecraft", "vanilla", "1"],
    "reciever": "id of reciever chest",
    "items": {
        "iron-ingot": {
            "amount": 1,
            "orig-name": "iron-plate",
            "input_count": 100,
            "output_count": 1
        }
    }
}
```
The extra info (orig-name and input/output count are needed for rejection)

The default game implementations do not use data!
