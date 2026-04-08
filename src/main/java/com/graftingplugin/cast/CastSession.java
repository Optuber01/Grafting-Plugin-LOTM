package com.graftingplugin.cast;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;

public final class CastSession {

    private GraftFamily family = GraftFamily.STATE;
    private GraftSubject source;
    private GraftAspect selectedAspect;

    public GraftFamily family() {
        return family;
    }

    public void setFamily(GraftFamily family) {
        this.family = family;
    }

    public GraftSubject source() {
        return source;
    }

    public void setSource(GraftSubject source) {
        this.source = source;
        this.selectedAspect = null;
    }

    public GraftAspect selectedAspect() {
        return selectedAspect;
    }

    public void setSelectedAspect(GraftAspect selectedAspect) {
        this.selectedAspect = selectedAspect;
    }

    public void clearAspectSelection() {
        this.selectedAspect = null;
    }

    public void clearSelection() {
        this.source = null;
        this.selectedAspect = null;
    }
}
