package com.graftingplugin.cast;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;

public final class CastSession {

    private GraftFamily family = GraftFamily.STATE;
    private GraftSubject source;
    private CastSourceReference sourceReference = CastSourceReference.none();
    private GraftAspect selectedAspect;
    private String selectedStatusEffectKey;
    private int selectedTargetSlot = -1;

    public GraftFamily family() {
        return family;
    }

    public GraftFamily effectiveFamily() {
        return family;
    }

    public void setFamily(GraftFamily family) {
        this.family = family;
    }

    public GraftSubject source() {
        return source;
    }

    public void setSource(GraftSubject source, CastSourceReference sourceReference) {
        this.source = source;
        this.sourceReference = sourceReference;
        this.selectedAspect = null;
        this.selectedStatusEffectKey = null;
    }

    public CastSourceReference sourceReference() {
        return sourceReference;
    }

    public GraftAspect selectedAspect() {
        return selectedAspect;
    }

    public void setSelectedAspect(GraftAspect selectedAspect) {
        this.selectedAspect = selectedAspect;
        if (selectedAspect != GraftAspect.STATUS) {
            this.selectedStatusEffectKey = null;
        }
    }

    public String selectedStatusEffectKey() {
        return selectedStatusEffectKey;
    }

    public void setSelectedStatusEffectKey(String selectedStatusEffectKey) {
        this.selectedStatusEffectKey = selectedStatusEffectKey;
    }

    public void clearAspectSelection() {
        this.selectedAspect = null;
        this.selectedStatusEffectKey = null;
    }

    public int selectedTargetSlot() {
        return selectedTargetSlot;
    }

    public void setSelectedTargetSlot(int slot) {
        this.selectedTargetSlot = slot;
    }

    public void clearTargetSlotSelection() {
        this.selectedTargetSlot = -1;
    }

    public void clearSelection() {
        this.source = null;
        this.sourceReference = CastSourceReference.none();
        this.selectedAspect = null;
        this.selectedStatusEffectKey = null;
        this.selectedTargetSlot = -1;
    }
}
