commands.add_command('scis_airbridge.add', 'adds items into a chest', function(command)
    if command.player_index then
        return
    end
    rcon.print("hi")
end)

commands.add_command('scis_airbridge.get', 'Gets all players', function(command)
    if command.player_index then
        return
    end
    rcon.print("Bye")
end)
