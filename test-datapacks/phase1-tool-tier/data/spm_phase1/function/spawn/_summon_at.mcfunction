kill @e[type=playermob:player_mob,tag=spm_p1_test]
playermob summon Steve ~ ~ ~ named P1Test
tag @e[type=playermob:player_mob,sort=nearest,limit=1] add spm_p1_test
playermob stay P1Test here 32
