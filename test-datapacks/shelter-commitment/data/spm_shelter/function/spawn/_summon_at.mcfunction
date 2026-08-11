kill @e[type=playermob:player_mob,tag=spm_shelter_test]
playermob summon Steve ~ ~1 ~ named ShelterTest
tag @e[type=playermob:player_mob,sort=nearest,limit=1] add spm_shelter_test
