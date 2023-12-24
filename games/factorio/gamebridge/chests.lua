local function chest_to_index(chest)
    return chest.surface_index .. "_" .. chest.position.x .. "_" .. chest.position.y .. "_" .. chest.name
end

local function index_to_chest(index)
    local split = {}
    for v in string.gmatch(index, "([^_]+)") do
        table.insert(split, v)
    end
    local surface_index = tonumber(split[1])
    local position = { tonumber(split[2]), tonumber(split[3]) }
    local entity_name = split[4]
    local entity = game.get_surface(surface_index).find_entity(entity_name, position)
    if entity and entity.valid then
        return entity
    end
    return nil
end

local function remove_chest(chest_index)
    local data = global.chest_data[chest_index]
    if not data then
        return
    end
    global.chest_data[chest_index] = nil
    local chest_type = data.chest_type
    local chest_id = data.chest_id
    if chest_type and chest_type ~= "none" and chest_id then
        global[chest_type .. "_chests"][chest_id][chest_index] = nil
    end
end

local function on_chest_removed(event)
    local entity = event.entity
    if not entity then
        return
    end
    if entity.type ~= "container" then
        return
    end

    local index = chest_to_index(entity)
    remove_chest(index)
end

local function on_surface_removed(event)
    local prefix = event.surface_index .. "_"
    local len = #prefix
    for index, _ in pairs(global.chest_data) do
        if string.sub(1, len) == prefix then
            remove_chest(index)
        end
    end
end


local function add_item_to_chests(chest_id, items)
    local overflow = {}
    local chests = global.receiver_chests[chest_id]
    local chest_count = 0
    for _ in pairs(chests) do chest_count = chest_count + 1 end
    for chest_index, _ in pairs(chests) do
        local chest = index_to_chest(chest_index)
        local inventory = chest.get_inventory(defines.inventory.chest)
        if not inventory then
            goto continue
        end
        for item, count in pairs(items) do
            local actual_count = math.floor(count / chest_count)
            local inserted = inventory.insert({ name = item, count = actual_count })
            --TODO handle overflow
            local pre_overflow = overflow[item] or 0
            overflow[item] = (actual_count - inserted) + pre_overflow
        end
        ::continue::
    end

    return overflow
end

local function get_items_from_chests(debug)
    local items_id = {}
    --game.print(game.table_to_json(global.sender_chests))
    for id, chests in pairs(global.sender_chests) do
        --game.print("Checking for chests with id " .. id)
        local items = {}
        for chest_index, _ in pairs(chests) do
            local chest = index_to_chest(chest_index)
            local inventory = chest.get_inventory(defines.inventory.chest)
            if not inventory then
                --game.print("Chest " .. chest_index .. " has no inventory")
                goto continue
            end
            local chest_items = inventory.get_contents()
            if not debug then
                inventory.clear()
            end
            --game.print("items " .. game.table_to_json(chest_items))
            for item, count in pairs(chest_items) do
                local prev_count = items[item] or 0
                items[item] = count + prev_count
            end
            ::continue::
        end
        items_id[id] = items
    end
    return items_id
end

local function set_chest(chest_index, chest_data, old_data)
    local chest_type = chest_data.chest_type
    local chest_id   = chest_data.chest_id
    local old_type   = old_data.chest_type or chest_type
    local old_id     = old_data.chest_id or chest_id

    if old_type and old_type ~= "none" and old_id then
        local chests = global[old_type .. "_chests"][old_id] or {}
        chests[chest_index] = nil --set like structure
        global[old_type .. "_chests"][old_id] = chests
    end
    if chest_type and chest_type ~= "none" and chest_id then
        local chests = global[chest_type .. "_chests"][chest_id] or {}
        chests[chest_index] = true --set like structure
        global[chest_type .. "_chests"][chest_id] = chests
    end
end

return {
    on_chest_removed = on_chest_removed,
    on_surface_removed = on_surface_removed,
    add_item_to_chests = add_item_to_chests,
    get_items_from_chests = get_items_from_chests,
    set_chest = set_chest,
    chest_to_index = chest_to_index,
    index_to_chest = index_to_chest,
}
