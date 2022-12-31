# GameBridge

A system designed for multiple games to work together

e.g. Producing iron in minecraft and process it in factorio to steel

## documentation
comming soon™

## conversion

The specialized converters have priority over the more general

A conversion will happen in this order:
- origin_type->origin_name->origin_id
- origin_type->origin_name->default
- origin_type->default
- default

A conversion can have on of 4 results:
- converted
    
    a successful conversion happend
- anyway

    we use the result anyway (mostly used for sending items / the specialized converters)
- reject

    reject the input item

    cases:
    - sending
        
        return it to the sending chest

    - recieving

        send a reject notification to the origin
- delete

    delete the input item