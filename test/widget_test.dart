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
      ChangeNotifierProvider(
        create: (_) => AssistantController(HeyDarlingBridge()),
        child: const HeyDarlingApp(),
      ),
    );

    // Allow one frame for rendering
    await tester.pump();

    // Test passes if app builds without errors
    expect(find.byType(HeyDarlingApp), findsOneWidget);
  });
}
