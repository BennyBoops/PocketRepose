package net.bennyboops.modid.criterion;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class EnterPocketDimensionCriterion
        extends AbstractCriterion<EnterPocketDimensionCriterion.Conditions> {

    public static final Identifier ID = Identifier.of("pocket-repose", "enter_pocket_dimension");


    public Identifier getId() {
        return ID;
    }

    protected Conditions conditionsFromJson(JsonObject json, LootContextPredicate playerPredicate) {
        return new Conditions(playerPredicate);
    }

    public void trigger(ServerPlayerEntity player) {
        this.trigger(player, c -> true);
    }

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return null;
    }

    public static class Conditions implements AbstractCriterion.Conditions {
        public Conditions(LootContextPredicate playerPredicate) {
            super();
        }

        @Override
        public Optional<LootContextPredicate> player() {
            return Optional.empty();
        }

    }
}