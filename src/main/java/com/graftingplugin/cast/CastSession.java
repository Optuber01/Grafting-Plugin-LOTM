package com.graftingplugin.cast;

public final class CastSession {

    private GraftFamily family = GraftFamily.STATE;
    private String pendingConcept;

    public GraftFamily family() {
        return family;
    }

    public void setFamily(GraftFamily family) {
        this.family = family;
    }

    public String pendingConcept() {
        return pendingConcept;
    }

    public void setPendingConcept(String pendingConcept) {
        this.pendingConcept = pendingConcept;
    }

    public void clearSelection() {
        this.pendingConcept = null;
    }
}
