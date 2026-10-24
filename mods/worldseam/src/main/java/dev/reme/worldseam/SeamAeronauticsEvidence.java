package dev.reme.worldseam;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

/**
 * Strict observational verification that a filmed Sable structure actually
 * contains ACTUATED Create:Aeronautics drivetrain / propeller actors.
 *
 * An 18-stone-block raft or decorative blue aircraft pushed externally by
 * WorldSeam test impulses CANNOT pass this gate. Never fake an Aeronautics
 * propeller via manually adding force to a Rapier body.
 *
 * Server-only reflection: keeps Aeronautics/Sable optional dependencies off
 * the independent WorldSeam compile classpath. The released mods must be
 * installed and genuine runtime BE actors must be found in the Sable plot.
 */
public final class SeamAeronauticsEvidence {
    private SeamAeronauticsEvidence() {}

    public record Observation(int aeronauticsPropellerActors,
                              int spinningPropellers,
                              double totalAbsoluteThrust,
                              String diagnostic) {
        /** True only for actually spinning Aero actor, never external force. */
        public boolean powered() {
            return spinningPropellers>0 &&
                totalAbsoluteThrust>0 &&
                Double.isFinite(totalAbsoluteThrust);
        }
    }

    public static Observation inspect(Object actualServerSubLevel) {
        if(actualServerSubLevel==null)
            return new Observation(0,0,0,"missing real Sable sublevel");
        try {
            Class<?> propType=Class.forName(
                "dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller");
            Object plot=actualServerSubLevel.getClass()
                .getMethod("getPlot").invoke(actualServerSubLevel);
            if(plot==null)return new Observation(0,0,0,"missing Sable plot");
            // The actual public plot class exposes getBlockEntityActors().
            Object object=plot.getClass().getMethod("getBlockEntityActors")
                .invoke(plot);
            if(!(object instanceof Collection<?> actors))
                return new Observation(0,0,0,"Sable plot actor registry unavailable");
            int count=0,live=0;
            double thrust=0;
            for(Object actor:actors) {
                if(actor==null || !propType.isInstance(actor) ||
                   !actor.getClass().getName()
                       .startsWith("dev.eriksonn.aeronautics."))continue;
                count++;
                boolean active=(Boolean)propType.getMethod("isActive")
                    .invoke(actor);
                double available=((Number)propType.getMethod("getThrust")
                    .invoke(actor)).doubleValue();
                // Must be a Create kinetic actor, not just a modded propeller
                // that returns a nonzero configured thrust independent of RPM.
                double rpm=((Number)actor.getClass().getMethod("getSpeed")
                    .invoke(actor)).doubleValue();
                if(active && Double.isFinite(rpm) && Math.abs(rpm)>0.01 &&
                   Double.isFinite(available) && Math.abs(available)>0.001) {
                    live++;
                    thrust+=Math.abs(available);
                }
            }
            return new Observation(count,live,thrust,
                "real Aero propeller actors="+count+", genuinely powered="+live);
        } catch(ClassNotFoundException e) {
            return new Observation(0,0,0,"Sable propulsion API unavailable");
        } catch(ReflectiveOperationException | RuntimeException e) {
            Throwable cause=e instanceof InvocationTargetException
                && e.getCause()!=null ? e.getCause():e;
            return new Observation(0,0,0,
                "Cannot inspect live Aero KineticBlockEntity: "+cause);
        }
    }
}
