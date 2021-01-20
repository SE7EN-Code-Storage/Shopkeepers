package com.nisovin.shopkeepers.shopobjects.entity;

import org.bukkit.entity.Entity;

import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopobjects.entity.EntityShopObject;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObject;

public abstract class AbstractEntityShopObject extends AbstractShopObject implements EntityShopObject {

	protected AbstractEntityShopObject(AbstractShopkeeper shopkeeper, ShopCreationData creationData) {
		super(shopkeeper, creationData);
	}

	@Override
	public abstract AbstractEntityShopObjectType<?> getType();

	@Override
	public boolean isActive() {
		return (this.getEntity() != null);
	}

	@Override
	public Object getId() {
		Entity entity = this.getEntity();
		if (entity == null) return null; // Not active
		return this.getType().getObjectId(entity);
	}
}
