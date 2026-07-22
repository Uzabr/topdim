/** Keep in sync with identity-service StrongPasswordValidator. */
export const STRONG_PASSWORD_PATTERN =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()\-_=+])[A-Za-z\d@$!%*?&#^()\-_=+]{8,128}$/;

export function isStrongPassword(password: string): boolean {
  return STRONG_PASSWORD_PATTERN.test(password);
}
