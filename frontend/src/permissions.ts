import type {UserRole} from "./types";

export type AppCapability = "algorithms.view" | "users.manage";

const CAPABILITY_ROLES: Record<AppCapability, readonly UserRole[]> = {
    "algorithms.view": ["maintainer", "admin"],
    "users.manage": ["admin"],
};

export function canAccessCapability(role: string, capability: AppCapability): boolean {
    return CAPABILITY_ROLES[capability].includes(role as UserRole);
}
