package net.bennyboops.modid.criterion;

import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class EnterPocketDimensionCriterion extends AbstractCriterion<EnterPocketDimensionCriterion.Conditions> {
    static final Identifier ID = new Identifier("pocket-repose", "enter_pocket_dimension");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Conditions conditionsFromJson(JsonObject jsonObject, LootContextPredicate lootContextPredicate, AdvancementEntityPredicateDeserializer advancementEntityPredicateDeserializer) {
        return new Conditions(lootContextPredicate);
    }

    public void trigger(ServerPlayerEntity player) {
        this.trigger(player, conditions -> true);
    }

    public static class Conditions extends AbstractCriterionConditions {
        public Conditions(LootContextPredicate player) {
            super(EnterPocketDimensionCriterion.ID, player);
        }

        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            return super.toJson(predicateSerializer);
        }
    }
}