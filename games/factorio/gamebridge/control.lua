commands.add_command('scis_gamebridge.add_items', 'adds items into a chest', function(command)
    if command.player_index then
        return
    end
    if not command.parameter then
        return
    end
    local parameter = game.json_to_table(command.parameter)
end)

commands.add_command('scis_gamebridge.get_items', 'Gets all items indexed by chest', function(command)
    if command.player_index then
        return
    end
    rcon.print("Bye")
end)

commands.add_command('scis_gamebridge.get_chests', 'Gets all new chest infos', function(command)
    if command.player_index then
        return
    end

    rcon.print("NAAAA")
end)
