-- TodoMVC implementation using gui-lite.
-- The following is how I (raiguard) prefer to structure GUIs, but it is not the only way.

-- GUI


--[[
    Chest settings GUI:
    GameBridge label
    switch to select chest type (none, send, receive)
    textfield for id
    TODO:
    switch to select white/blacklist

]]

local flib_gui = require("__flib__/gui-lite")
local mod_gui = require("__core__/lualib/mod-gui")
require("constants")
local chest_funcs = require("chests")
local set_chest = chest_funcs.set_chest
local chest_to_index = chest_funcs.chest_to_index


--- @alias ChestMode
--- | "sender"
--- | "none"
--- | "receiver"

--- @class ChestSettingsGui
--- @field elems table<string, LuaGuiElement>
--- @field player LuaPlayer

--- @class FlibTestGuiBase
local gui = {}


--TODO change captions to locale strings

--- Build the GUI for the given player.
--- @param player LuaPlayer
function gui.build(player)
    -- `elems` is a table consisting of all GUI elements that were given names, keyed by their name.
    -- `window` is the GUI element that was created first, which in this case, was the top-level frame.
    -- The second argument can be a single element or an array of elements. Here we pass a single element.
    local elems = flib_gui.add(player.gui.relative, {
        type = "frame",
        name = Mod_Prefix.."chest_settings_gui",
        direction = "vertical",
        anchor = { gui = defines.relative_gui_type.container_gui, position = defines.relative_gui_position.right },
        -- If `handler` is a function, it will call that function for any GUI event on this element.
        -- If it is a dictioanry of event -> function, it will call the corresponding function for the corresponding event.
        handler = { [defines.events.on_gui_closed] = gui.on_window_closed,
                    [defines.events.on_gui_opened] = gui.on_window_opened},
        -- Children can be defined as array members of an element.
        {
            type = "flow",
            style = "flib_titlebar_flow",
            { type = "label", style = "frame_title", caption = "GameBridge", ignored_by_interaction = true },
        },
        {
            type = "frame",
            name = Mod_Prefix .. "inner_frame",
            style = "inside_shallow_frame",
            direction = "vertical",
            {
                type = "switch",
                name = Mod_Prefix .. "chest_type_switch",
                allow_none_state = true,
                switch_state = "none",
                left_label_caption = "Sender",
                left_label_tooltip = "Mark this chest as a sending chest",
                right_label_caption = "Receiver",
                right_label_tooltip = "Mark this chest as a receiving chest",
                handler = { [defines.events.on_gui_switch_state_changed] = gui.on_gui_switch_state_changed },
            },
            {
                type = "textfield",
                name = Mod_Prefix .. "id_textfield",
                caption = "ID",
                style = "flib_widthless_textfield",
                style_mods = { horizontally_stretchable = true },
                handler = {
                    -- Multiple different event handlers
                    [defines.events.on_gui_confirmed] = gui.on_textfield_confirmed,
                    [defines.events.on_gui_text_changed] = gui.on_textfield_text_changed,
                },
            },
        },

    })

    -- In a real mod, you would want to initially hide the GUI and not set opened until the player opens it.
    -- player.opened = elems.flib_todo_window
    --- @type ChestSettingsGui
    global.guis[player.index] = {
        elems = elems,
        player = player,
    }
end

--- @param e EventData.on_gui_switch_state_changed
function gui.on_gui_switch_state_changed(self, e)
    local chest = self.player.opened
    local index = chest_to_index(chest)

    local state = e.element.switch_state
    local chest_type = "none"

    if state == "left" then
        chest_type = "sender"
    elseif state == "right" then
        chest_type = "receiver"
    end


    local data = global.chest_data[index] or {}
    local old_type = data.chest_type
    data.chest = chest
    data.chest_type = chest_type
    global.chest_data[index] = data
    set_chest(index, data, {["chest_type"] = old_type})
end

--- @param e EventData.on_gui_confirmed
function gui.on_textfield_text_changed(self, e)
    --nothing todo?
end


--- @param self ChestSettingsGui
function gui.hide(self)
    self.elems.flib_todo_window.visible = false
end


--- @param self ChestSettingsGui
--- @param e EventData.on_gui_confirmed
function gui.on_textfield_confirmed(self, e)
    local chest = self.player.opened
    local index = chest_to_index(chest)

    local chest_id = e.element.text

    if chest_id == "" then
        chest_id = nil
    end

    local data = global.chest_data[index] or {}
    local old_id = data.chest_id
    data.chest = chest
    data.chest_id = chest_id
    global.chest_data[index] = data

    set_chest(index, data, {["chest_id"] = old_id})
end

--- @param self ChestSettingsGui
function gui.on_window_closed(self)
    gui.hide(self)
end

--- @param self ChestSettingsGui
function gui.on_window_opened(self)
    local chest = self.player.opened
    local index = chest_to_index(chest)
    local data = global.chest_data[index] or {}
    local chest_type = data.chest_type or "none"
    local chest_id = data.chest_id or ""
    self.elems[Mod_Prefix .. "id_textfield"].text = chest_id
    if chest_type == "sender" then
        self.elems[Mod_Prefix .. "chest_type_switch"].switch_state = "left"
    elseif chest_type == "receiver" then
        self.elems[Mod_Prefix .. "chest_type_switch"].switch_state = "right"
    end
end

--- @param self ChestSettingsGui
function gui.show(self)
    self.elems[Mod_Prefix.."chest_settings_gui"].visible = true
    self.elems[Mod_Prefix .. "id_textfield"].focus()
end

--- @param self ChestSettingsGui
function gui.toggle_visible(self)
    if self.elems[Mod_Prefix .. "chest_settings_gui"].visible then
        gui.hide(self)
    else
        gui.show(self)
    end
end


-- Add all functions in the `gui` table as callable handlers. This is required in order for functions in `gui.add` to
-- work. For convenience, flib will ignore any value that isn't a function.
-- The second argument is an optional wrapper function that will be called in lieu of the specified handler of an
-- element. It is used in this case to get the GUI table for the corresponding player before calling the handler.
flib_gui.add_handlers(gui, function(e, handler)
    local self = global.guis[e.player_index]
    if self then
        handler(self, e)
    end
end)

-- BOOTSTRAP

-- Handle all 'on_gui_*' events with `flib_gui.dispatch`. If you don't call this, then your element handlers won't work!
-- If you wish to have custom logic for a specific GUI event, you can call `flib_gui.dispatch` yourself in your main
-- event handler. `handle_events` will not override any existing event handlers.
flib_gui.handle_events()

-- Create the GUI when a player is created
script.on_event(defines.events.on_player_created, function(e)
    local player = game.get_player(e.player_index) --[[@as LuaPlayer]]
    gui.build(player)
end)

script.on_event(defines.events.on_gui_opened, function (e)
    local player = game.get_player(e.player_index)
    if not e.gui_type == defines.gui_type.entity then
        return
    end
    if not player.opened then
        return
    end
    local chest = player.opened
    local index = chest_to_index(chest)
    local data = global.chest_data[index] or {}
    local chest_type = data.chest_type or "none"
    local chest_id = data.chest_id or ""
    local elems = global.guis[e.player_index].elems
    elems[Mod_Prefix .. "id_textfield"].text = chest_id
    if chest_type == "sender" then
        elems[Mod_Prefix .. "chest_type_switch"].switch_state = "left"
    elseif chest_type == "receiver" then
        elems[Mod_Prefix .. "chest_type_switch"].switch_state = "right"
    else
        elems[Mod_Prefix .. "chest_type_switch"].switch_state = "none"
    end
end)

-- For a real mod, you would also want to handle on_configuration_changed to rebuild your GUIs, and on_player_removed
-- to remove the GUI table from global. You would also want to ensure that the GUI is valid before running methods.
-- For the sake of brevity, these things were not covered in this demo.


return gui
