package com.nisovin.shopkeepers.shopkeeper.spawning;

enum SpawnResult {
	IGNORED,
	IGNORED_INACTIVE,
	SPAWNED,
	ALREADY_SPAWNED,
	SPAWNING_FAILED,
	QUEUED,
	AWAITING_WORLD_SAVE_RESPAWN,
	DESPAWNED_AND_AWAITING_WORLD_SAVE_RESPAWN;
}