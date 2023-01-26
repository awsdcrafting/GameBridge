commands.add_command('scis_gamebridge.add', 'adds items into a chest', function(command)
    if command.player_index then
        return
    end
    if not command.parameter then
        return
    end
    game.json_to_table(command.parameter)
end)

commands.add_command('scis_gamebridge.get', 'Gets all items indexed by chest', function(command)
    if command.player_index then
        return
    end
    rcon.print("Bye")
end)
