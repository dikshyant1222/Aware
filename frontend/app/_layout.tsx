import { Stack, router, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { AlertOverlay } from '@/components/AlertOverlay';
import { AwareProvider, useAware } from '@/aware/AwareContext';
import { colors } from '@/theme';

function Navigation() {
  const { hydrated, onboardingComplete } = useAware();
  const segments = useSegments();

  useEffect(() => {
    if (hydrated && !onboardingComplete && segments[0] !== 'onboarding') router.replace('/onboarding');
  }, [hydrated, onboardingComplete, segments]);

  if (!hydrated) {
    return <View style={styles.loading}><ActivityIndicator size="large" color={colors.primary} /></View>;
  }

  return (
    <>
      <StatusBar style="light" />
      <Stack screenOptions={{ contentStyle: { backgroundColor: colors.background }, headerStyle: { backgroundColor: colors.surface }, headerTintColor: colors.text, headerTitleStyle: { fontWeight: '800' } }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="onboarding" options={{ headerShown: false, gestureEnabled: false }} />
        <Stack.Screen name="teach" options={{ title: 'Teach a Sound' }} />
        <Stack.Screen name="privacy" options={{ title: 'Privacy' }} />
      </Stack>
      <AlertOverlay />
    </>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AwareProvider><Navigation /></AwareProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
});
