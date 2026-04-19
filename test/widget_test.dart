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
  testWidgets('App renders status controls', (WidgetTester tester) async {
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => AssistantController(SilentAssistantBridge()),
        child: const SilentAssistantApp(),
      ),
    );

    expect(find.text('Darling 😘 Assistant App', skipOffstage: false), findsOneWidget);
    expect(find.text('Start Listening', skipOffstage: false), findsOneWidget);
    expect(find.text('Stop', skipOffstage: false), findsOneWidget);
  });
}
