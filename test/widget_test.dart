// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:silentoapp/main.dart';

void main() {
  testWidgets('App initializes with HeyyDarling branding', (WidgetTester tester) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(
            create: (_) => ThemeProvider(),
          ),
          ChangeNotifierProvider(
            create: (_) => AssistantController(HeyDarlingBridge())..initialize(),
          ),
        ],
        child: const HeyDarlingApp(),
      ),
    );

    // Pump for splash screen animation (2s)
    await tester.pump(const Duration(milliseconds: 2000));
    
    // Pump for the delayed navigation (3s)
    await tester.pump(const Duration(seconds: 3));

    // Test passes if app builds without errors
    expect(find.byType(HeyDarlingApp), findsOneWidget);
  });
}
