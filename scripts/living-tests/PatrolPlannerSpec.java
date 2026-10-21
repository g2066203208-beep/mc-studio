package dev.reme.mcstudio.npc.life;

public final class PatrolPlannerSpec {
    public static void main(String[] args) {
        var seen = new java.util.HashSet<String>();
        for (int i=0; i<4; i++) {
            var step=PatrolPlanner.choose(i*260L, 0);
            if (Math.abs(step.dx())+Math.abs(step.dz())!=8)
                throw new AssertionError("outside bounded eight-block patrol ring");
            seen.add(step.dx()+","+step.dz());
        }
        if(seen.size()!=4) throw new AssertionError("patrol quadrants do not rotate");
        if(!PatrolPlanner.choose(-1, -51).equals(PatrolPlanner.choose(-1+1040, -51)))
            throw new AssertionError("negative tick/id patrol parity");
        System.out.println("PatrolPlannerSpec: 6 assertions passed");
    }
}
