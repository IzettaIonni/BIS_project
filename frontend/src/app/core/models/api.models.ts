export interface User {
    id: number;
    username: string;
    bio?: string;
    avatar?: string | null;
}

export interface LoginResponse {
    access: string;
    refresh: string;
    user: User;
}