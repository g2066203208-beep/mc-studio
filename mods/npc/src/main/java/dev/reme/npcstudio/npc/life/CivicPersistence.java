package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Persist eyewitness evidence, personality and escrowed contract per resident. */
public final class CivicPersistence {
    private CivicPersistence() {}

    public static void save(PlayerNpcEntity npc, CompoundTag tag) {
        CharacterProfile p = npc.character();
        tag.putInt("CivicHonesty", p.honesty());
        tag.putInt("CivicJustice", p.justice());
        tag.putInt("CivicCuriosity", p.curiosity());
        tag.putInt("CivicEmpathy", p.empathy());
        ListTag records = new ListTag();
        for (CivicMind.Report report : npc.civic().reports()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", report.id());
            entry.putUUID("Actor", report.actor());
            entry.putUUID("Witness", report.firstWitness());
            entry.putString("Kind", report.kind().name());
            entry.putLong("Tick", report.tick());
            entry.putInt("Hops", report.hops());
            entry.putBoolean("Direct", report.direct());
            records.add(entry);
        }
        tag.put("CivicEvidence", records);
        if (npc.commission() != null) {
            Commission job = npc.commission();
            CompoundTag data = new CompoundTag();
            data.putString("Goods", job.goods().name());
            data.putInt("Quantity", job.quantity());
            data.putInt("Reward", job.emeraldReward());
            data.putString("State", job.state().name());
            if (job.contractor() != null) data.putUUID("Contractor", job.contractor());
            tag.put("CivicCommission", data);
        }
    }

    public static void load(PlayerNpcEntity npc, CompoundTag tag) {
        npc.setCharacter(new CharacterProfile(
            clamp(tag, "CivicHonesty", 60),
            clamp(tag, "CivicJustice", 65),
            clamp(tag, "CivicCuriosity", 60),
            clamp(tag, "CivicEmpathy", 60)));
        npc.civic().clear();
        ListTag records = tag.getList("CivicEvidence", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(48, records.size()); i++) {
            CompoundTag data = records.getCompound(i);
            if (!data.hasUUID("Id") || !data.hasUUID("Actor") ||
                !data.hasUUID("Witness")) continue;
            try {
                npc.civic().restore(new CivicMind.Report(
                    data.getUUID("Id"), data.getUUID("Actor"), data.getUUID("Witness"),
                    CivicMind.Kind.valueOf(data.getString("Kind")),
                    Math.max(0, data.getLong("Tick")),
                    Math.clamp(data.getInt("Hops"), 0, 2),
                    data.getBoolean("Direct")));
            } catch (IllegalArgumentException ignored) { /* tolerate old saves */ }
        }
        npc.setCommission(null);
        if (!tag.contains("CivicCommission", Tag.TAG_COMPOUND)) return;
        CompoundTag data = tag.getCompound("CivicCommission");
        try {
            Commission job = new Commission(
                Commission.Goods.valueOf(data.getString("Goods")),
                data.getInt("Quantity"), data.getInt("Reward"));
            job.restore(Commission.State.valueOf(data.getString("State")),
                data.hasUUID("Contractor") ? data.getUUID("Contractor") : null);
            npc.setCommission(job);
        } catch (IllegalArgumentException ignored) { /* invalid record */ }
    }

    private static int clamp(CompoundTag data, String name, int fallback) {
        return Math.clamp(data.contains(name, Tag.TAG_INT) ?
            data.getInt(name) : fallback, 0, 100);
    }
}
