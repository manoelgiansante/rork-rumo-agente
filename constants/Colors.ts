// AgroRumo Brand Colors
export const BrandColors = {
  primary: '#2D5016',
  primaryLight: '#4A7A2E',
  primaryDark: '#1A3009',
  secondary: '#FDD835',
  secondaryLight: '#FFEB3B',
  secondaryDark: '#F9A825',
} as const;

export const Colors = {
  light: {
    text: '#1A1A1A',
    background: '#FFFFFF',
    surface: '#F5F5F5',
    tint: BrandColors.primary,
    accent: BrandColors.secondary,
    border: '#E0E0E0',
    icon: '#687076',
    tabIconDefault: '#687076',
    tabIconSelected: BrandColors.primary,
  },
  dark: {
    text: '#ECEDEE',
    background: '#151718',
    surface: '#1E2022',
    tint: BrandColors.primaryLight,
    accent: BrandColors.secondary,
    border: '#333333',
    icon: '#9BA1A6',
    tabIconDefault: '#9BA1A6',
    tabIconSelected: BrandColors.primaryLight,
  },
};
