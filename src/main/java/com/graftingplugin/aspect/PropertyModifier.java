package com.graftingplugin.aspect;

public record PropertyModifier(
    double durationMultiplier,
    int amplifier,
    double radiusMultiplier,
    double intensity
) {

    public static final PropertyModifier BASE = new PropertyModifier(1.0, 0, 1.0, 1.0);

    public static PropertyModifier fromProfile(GraftAspect aspect, DynamicPropertyProfile profile) {
        if (profile == null || profile == DynamicPropertyProfile.EMPTY) {
            return BASE;
        }

        double durationMult = 1.0;
        int amp = 0;
        double radiusMult = 1.0;
        double intensity = 1.0;

        switch (aspect) {
            case HEAVY -> {
                double mass = profile.get(DynamicProperty.MASS);
                if (mass >= 10.0) {
                    amp = 2;
                    intensity = 2.0;
                } else if (mass >= 3.0) {
                    amp = 1;
                    intensity = 1.5;
                }
                durationMult = 1.0 + (mass / 20.0);
            }
            case SLOW, STICKY -> {
                double mass = profile.get(DynamicProperty.MASS);
                if (mass >= 5.0) {
                    amp = 1;
                }
                durationMult = 1.0 + (mass / 30.0);
            }
            case FREEZE -> {
                double mass = profile.get(DynamicProperty.MASS);
                double thermal = profile.get(DynamicProperty.THERMAL);
                if (mass >= 5.0) {
                    amp = 1;
                }
                durationMult = 1.0 + (mass / 30.0) + Math.max(0, -thermal) * 0.5;
                intensity = 1.0 + Math.max(0, -thermal) * 0.3;
            }
            case TETHER, AGGRO -> {
                double mass = profile.get(DynamicProperty.MASS);
                durationMult = 1.0 + (mass / 20.0);
                intensity = 1.0 + (mass / 10.0);
            }
            case HEAT, IGNITE -> {
                double thermal = profile.get(DynamicProperty.THERMAL);
                if (thermal >= 2.0) {
                    amp = 1;
                }
                durationMult = 1.0 + Math.max(0, thermal) * 0.5;
                intensity = 1.0 + Math.max(0, thermal) * 0.5;
                radiusMult = 1.0 + Math.max(0, thermal) * 0.25;
            }
            case LIGHT, GLOW -> {
                double luminance = profile.get(DynamicProperty.LUMINANCE);
                if (luminance >= 2.0) {
                    amp = 1;
                }
                durationMult = 1.0 + luminance * 0.3;
                radiusMult = 1.0 + luminance * 0.5;
            }
            case SPEED, BOUNCE -> {
                double motility = profile.get(DynamicProperty.MOTILITY);
                if (motility > 0.3) {
                    amp = 1;
                }
                intensity = 1.0 + motility * 2.0;
            }
            case TARGET, RECEIVER -> {
                double motility = profile.get(DynamicProperty.MOTILITY);
                if (motility > 0.3) {
                    amp = 1;
                }
                durationMult = 1.0 + motility * 0.5;
                intensity = 1.0 + motility * 2.0;
            }
            case HEAL -> {
                double vitality = profile.get(DynamicProperty.VITALITY);
                double integrity = profile.get(DynamicProperty.INTEGRITY);
                double restorative = Math.max(vitality, integrity);
                if (restorative >= 4.0) {
                    amp = 2;
                } else if (restorative >= 1.5) {
                    amp = 1;
                }
                durationMult = 1.0 + restorative * 0.35;
                intensity = 1.0 + restorative * 0.4;
            }
            case POISON -> {
                double toxicity = profile.get(DynamicProperty.TOXICITY);
                if (toxicity >= 1.5) {
                    amp = 1;
                }
                if (toxicity >= 3.0) {
                    amp = 2;
                }
                durationMult = 1.0 + toxicity * 0.3;
                intensity = 1.0 + toxicity * 0.25;
            }
            case CONCEAL -> {
                double obscurity = profile.get(DynamicProperty.OBSCURITY);
                durationMult = 1.0 + obscurity * 0.35;
                intensity = 1.0 + obscurity * 0.2;
            }
            case DESTINATION, CONTAINER_LINK, ANCHOR, ENTRY, EXIT, NEAR, FAR, PATH_START, PATH_END, BEGIN, END, ON_OPEN -> {
                double maxProp = maxNonZero(profile);
                durationMult = 1.0 + maxProp * 0.1;
                radiusMult = 1.0 + maxProp * 0.1;
            }
            default -> {
            }
        }

        return new PropertyModifier(durationMult, amp, radiusMult, intensity);
    }

    public PropertyModifier withAmplifier(int newAmplifier) {
        return new PropertyModifier(durationMultiplier, newAmplifier, radiusMultiplier, intensity);
    }

    private static double maxNonZero(DynamicPropertyProfile profile) {
        double max = 0.0;
        for (DynamicProperty prop : DynamicProperty.values()) {
            max = Math.max(max, profile.get(prop));
        }
        return max;
    }
}
