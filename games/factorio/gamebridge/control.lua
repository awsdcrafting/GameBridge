local gui = require("gui")
local chest_funcs = require("chests")

Debug = false

local function command_add_items(command)
    if command.player_index and not Debug then
        return
    end
    if not command.parameter then
        return
    end
    local parameter = game.json_to_table(command.parameter)
    if parameter == nil then
        return
    end
    local chest_id = parameter.chest_id
    local items = parameter.items

    local overflow = chest_funcs.add_item_to_chests(chest_id, items)

    rcon.print(game.table_to_json(overflow))
end


local function command_get_items(command)
    if command.player_index then
        if Debug then
            game.print("got items " .. game.table_to_json(chest_funcs.get_items_from_chests(true)))
        end
        return
    end

    rcon.print(game.table_to_json(chest_funcs.get_items_from_chests(false)))
end

local function command_get_chests(command)
    if command.player_index then
        if Debug then
            game.print(game.table_to_json(global.chest_data))
        end
        return
    end

    rcon.print(game.table_to_json(global.chest_data))
end


local function command_debug(command)
    if command.player_index then
        game.players[command.player_index].print(serpent.block(global.sender_chests, {comment = true, refcomment = true, tablecomment = true}))
        game.players[command.player_index].print(serpent.block(global.receiver_chests, {comment = true, refcomment = true, tablecomment = true}))
        game.players[command.player_index].print(serpent.block(global.chest_data, {comment = true, refcomment = true, tablecomment = true}))
    end
    log(serpent.block(global.sender_chests, {comment = true, refcomment = true, tablecomment = true}))
    log(serpent.block(global.receiver_chests, {comment = true, refcomment = true, tablecomment = true}))
    log(serpent.block(global.chest_data, {comment = true, refcomment = true, tablecomment = true}))
end



local function register_commands()
    commands.add_command('scis_gamebridge.add_items', 'adds items into a chest', command_add_items)
    commands.add_command('scis_gamebridge.get_items', 'Gets all items indexed by chest', command_get_items)
    commands.add_command('scis_gamebridge.get_chests', 'Gets all new chest infos', command_get_chests)
    commands.add_command('scis_gamebridge.debug', 'Debug', command_debug)
end

--[[
    on entity destroyed: unregister chest

    on surface removed unregister all chests
]]

local function register_events()
    local filters_on_mined = {{ filter="type", type="container" }}
    script.on_event( defines.events.on_pre_player_mined_item, chest_funcs.on_chest_removed, filters_on_mined )
    script.on_event( defines.events.on_robot_pre_mined, chest_funcs.on_chest_removed, filters_on_mined )
    script.on_event( defines.events.on_entity_died, chest_funcs.on_chest_removed, filters_on_mined )
    script.on_event( defines.events.script_raised_destroy, chest_funcs.on_chest_removed )


    script.on_event( {defines.events.on_pre_surface_deleted, defines.events.on_pre_surface_cleared }, chest_funcs.on_surface_removed )
end

local function initialize()
    --setup global table
    global.guis = {}
    global.items_to_insert = {}
    global.sender_chests = {} --table from id to LuaEntity
    global.receiver_chests = {} --table from id to LuaEntity
    global.chest_data = {} --table from LuaEntity to data (id, type)
end

script.on_load(function()
    register_commands()
    register_events()
end)

script.on_init(function()
    initialize()

    register_commands()
    register_events()
end)

script.on_configuration_changed(function()
    initialize()

    --register_commands()
    register_events()
end)