kill @e[type=playermob:player_mob,tag=spm_p2_b]
playermob summon Alex ~ ~ ~ named P2B
tag @e[type=playermob:player_mob,sort=nearest,limit=1] add spm_p2_b
playermob stay P2B here 32
