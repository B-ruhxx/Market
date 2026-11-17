package com.tienda.ecommerce.crm;

public enum SegmentoCliente {
    VIP("VIP", "bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300 border-purple-300"),
    ACTIVO("Activo", "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 border-emerald-300"),
    EN_RIESGO("En Riesgo", "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300 border-amber-300"),
    PROSPECTO("Prospecto", "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300 border-blue-300"),
    INACTIVO("Inactivo", "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300 border-neutral-300");

    private final String label;
    private final String badgeClass;

    SegmentoCliente(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
