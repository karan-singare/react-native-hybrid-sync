import { NativeModules } from 'react-native';

export interface HybridSyncInterface {
  multiply(a: number, b: number): number;
}

const { HybridSync } = NativeModules;

export default HybridSync as HybridSyncInterface;
