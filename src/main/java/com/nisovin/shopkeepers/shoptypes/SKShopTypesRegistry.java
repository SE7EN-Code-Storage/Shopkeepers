package com.nisovin.shopkeepers.shoptypes;

import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.api.shoptypes.PlayerShopType;
import com.nisovin.shopkeepers.api.shoptypes.ShopTypesRegistry;
import com.nisovin.shopkeepers.types.AbstractSelectableTypeRegistry;

public class SKShopTypesRegistry extends AbstractSelectableTypeRegistry<AbstractShopType<?>> implements ShopTypesRegistry<AbstractShopType<?>> {

	@Override
	protected String getTypeName() {
		return "shop type";
	}

	@Override
	public boolean canBeSelected(Player player, AbstractShopType<?> type) {
		// TODO This currently skips all shop types that are not PlayerShopType
		// Maybe included the admin shop types here for players which are admins, because there /could/ be different
		// types of admin shops in the future
		// Maybe include other types of shop types here, is custom ones get registered?
		return super.canBeSelected(player, type) && (type instanceof PlayerShopType);
	}
}
