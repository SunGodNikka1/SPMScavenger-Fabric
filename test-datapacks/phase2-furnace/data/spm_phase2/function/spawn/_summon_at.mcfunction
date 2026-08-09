kill @e[type=playermob:player_mob,tag=spm_p2_test]
playermob summon Steve ~ ~ ~ named P2Test
tag @e[type=playermob:player_mob,sort=nearest,limit=1] add spm_p2_test
playermob stay P2Test here 32
