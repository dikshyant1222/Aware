import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import { colors, type } from '@/theme';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border, height: 76, paddingTop: 8, paddingBottom: 8 },
        tabBarLabelStyle: { fontSize: type.small, fontWeight: '800' },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Listen', tabBarAccessibilityLabel: 'Listen tab', tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="ear-hearing" color={String(color)} size={size} /> }} />
      <Tabs.Screen name="events" options={{ title: 'Events', tabBarAccessibilityLabel: 'Events tab', tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="history" color={String(color)} size={size} /> }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarAccessibilityLabel: 'Settings tab', tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="cog-outline" color={String(color)} size={size} /> }} />
    </Tabs>
  );
}
