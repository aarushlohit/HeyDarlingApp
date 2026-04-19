import 'dart:async';
import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (_) => AssistantController(HeyDarlingBridge())..initialize(),
      child: const HeyDarlingApp(),
    ),
  );
}

class HeyDarlingApp extends StatelessWidget {
  const HeyDarlingApp({super.key});

  @override
  Widget build(BuildContext context) {
    const accent = Color(0xFFE91E63);
    const accent2 = Color(0xFFFF80AB);

    final baseText = GoogleFonts.outfitTextTheme();

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'HeyyDarling 😘',
      theme: ThemeData(
        brightness: Brightness.light,
        scaffoldBackgroundColor: const Color(0xFFFFF7FB),
        textTheme: baseText.apply(bodyColor: const Color(0xFF2D0C1F), displayColor: const Color(0xFF2D0C1F)),
        colorScheme: const ColorScheme.light(
          primary: accent,
          secondary: accent2,
          surface: Color(0xFFFFF1F7),
          error: Color(0xFFD81B60),
        ),
        cardTheme: CardThemeData(
          color: Colors.white,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          elevation: 0,
          margin: EdgeInsets.zero,
        ),
        useMaterial3: true,
      ),
      home: const _ShellPage(),
    );
  }
}

class _ShellPage extends StatefulWidget {
  const _ShellPage();

  @override
  State<_ShellPage> createState() => _ShellPageState();
}

class _ShellPageState extends State<_ShellPage> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      const HomeScreen(),
      const LogsScreen(),
    ];

    return Scaffold(
      body: Stack(
        children: [
          const _AmbientBackdrop(),
          SafeArea(child: pages[_index]),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        backgroundColor: Colors.white,
        indicatorColor: const Color(0x33E91E63),
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.graphic_eq), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.timeline), label: 'Logs'),
        ],
      ),
    );
  }
}

class _AmbientBackdrop extends StatelessWidget {
  const _AmbientBackdrop();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFFFF7FB), Color(0xFFFFEFF7), Color(0xFFFFE4F1)],
          ),
        ),
        child: Stack(
          children: [
            Positioned(
              top: -120,
              left: -80,
              child: _GlowOrb(size: 260, color: Color(0x44E91E63)),
            ),
            Positioned(
              bottom: -120,
              right: -50,
              child: _GlowOrb(size: 230, color: Color(0x44FF80AB)),
            ),
          ],
        ),
      ),
    );
  }
}

class _GlowOrb extends StatelessWidget {
  const _GlowOrb({required this.size, required this.color});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: RadialGradient(
          colors: [color, Colors.transparent],
          stops: const [0.2, 1.0],
        ),
      ),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  DateTime? _lastTap;

  void _handleListeningTap(AssistantController controller) {
    final now = DateTime.now();
    if (_lastTap != null && now.difference(_lastTap!) < const Duration(milliseconds: 300)) {
      // Double tap - stop listening
      controller.stop();
      _lastTap = null;
    } else {
      _lastTap = now;
      // Single tap - start listening
      if (!controller.snapshot.isRunning) {
        controller.start();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<AssistantController>(
      builder: (context, controller, _) {
        final snapshot = controller.snapshot;
        final statusLabel = snapshot.statusLabel;
        final pendingLabel = snapshot.pendingModeLabel;

        return ListView(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          children: [
            Text(
              'HeyyDarling 😘',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.2,
                  ),
            ),
            const SizedBox(height: 24),
            _StatusPanel(
              statusLabel: statusLabel,
              pendingLabel: pendingLabel,
              snapshot: snapshot,
              onTap: () => _handleListeningTap(controller),
            ),
            const SizedBox(height: 24),
            _MicPulseIndicatorV2(
              isListening: snapshot.isRunning,
              isBusy: controller.isBusy,
              onTap: () => _handleListeningTap(controller),
            ),
            if (controller.permissionMessage != null) ...[
              const SizedBox(height: 14),
              _MessageCard(
                text: controller.permissionMessage!,
                color: Theme.of(context).colorScheme.error,
              ),
            ],
          ],
        );
      },
    );
  }
}

class LogsScreen extends StatelessWidget {
  const LogsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<AssistantController>(
      builder: (context, controller, _) {
        final logs = controller.snapshot.logs;
        return ListView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          children: [
            Row(
              children: [
                Text(
                  'Live Logs',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const Spacer(),
                TextButton.icon(
                  onPressed: controller.refresh,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Sync'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (logs.isEmpty)
              const _MessageCard(
                text: 'No events yet. Tap the listening icon to start.',
                color: Color(0xFFE91E63),
              )
            else
              ...logs.map(
                (line) => Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: const Color(0x33E91E63)),
                  ),
                  child: Text(line),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _StatusPanel extends StatelessWidget {
  const _StatusPanel({
    required this.statusLabel,
    required this.pendingLabel,
    required this.snapshot,
    required this.onTap,
  });

  final String statusLabel;
  final String pendingLabel;
  final ServiceSnapshot snapshot;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(32),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Colors.white.withValues(alpha: 0.25),
                  const Color(0xFFFFF1F7).withValues(alpha: 0.15),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(32),
              border: Border.all(
                color: Colors.white.withValues(alpha: 0.3),
                width: 1.5,
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFFE91E63).withValues(alpha: 0.15),
                  blurRadius: 25,
                  spreadRadius: 2,
                  offset: const Offset(0, 8),
                ),
                BoxShadow(
                  color: Colors.white.withValues(alpha: 0.5),
                  blurRadius: 15,
                  spreadRadius: -5,
                  offset: const Offset(0, -2),
                ),
              ],
            ),
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                children: [
                  // Larger Assistant Avatar
                  Container(
                    height: 280,
                    width: 220,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: [
                        BoxShadow(
                          color: const Color(0xFFE91E63).withValues(alpha: 0.4),
                          blurRadius: 40,
                          spreadRadius: 8,
                        )
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(24),
                      child: _AssistantImagePlaceholder(),
                    ),
                  ),
                  const SizedBox(height: 32),
                  // Status Header with Listening Icon
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        snapshot.isRunning ? Icons.graphic_eq_rounded : Icons.mic_none_rounded,
                        color: snapshot.statusColor,
                        size: 32,
                      ),
                      const SizedBox(width: 12),
                      Text(
                        'Service Status',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.5,
                            ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // Animated Listening Text
                  if (snapshot.isRunning)
                    _AnimatedListeningText(
                      text: statusLabel,
                      color: snapshot.statusColor,
                    )
                  else
                    Text(
                      statusLabel,
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                            color: snapshot.statusColor,
                            fontWeight: FontWeight.w800,
                          ),
                    ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      Expanded(
                        child: _GlassmorphicStatPill(
                          label: 'Running',
                          value: snapshot.isRunning ? 'Yes' : 'No',
                          icon: Icons.play_circle_fill,
                          isActive: snapshot.isRunning,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _GlassmorphicStatPill(
                          label: 'Pending',
                          value: pendingLabel,
                          icon: Icons.pending_actions,
                          isActive: pendingLabel != 'None',
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.2),
                        width: 1.2,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Last Command',
                          style: Theme.of(context)
                              .textTheme
                              .labelMedium
                              ?.copyWith(
                                color: Colors.grey.shade600,
                                fontWeight: FontWeight.w500,
                              ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          snapshot.lastCommand ?? 'None',
                          style: const TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 14,
                          ),
                        ),
                        const SizedBox(height: 10),
                        Text(
                          'Last Transcript',
                          style: Theme.of(context)
                              .textTheme
                              .labelMedium
                              ?.copyWith(
                                color: Colors.grey.shade600,
                                fontWeight: FontWeight.w500,
                              ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          snapshot.lastTranscript ?? 'None',
                          style: const TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AnimatedListeningText extends StatefulWidget {
  const _AnimatedListeningText({required this.text, required this.color});

  final String text;
  final Color color;

  @override
  State<_AnimatedListeningText> createState() => _AnimatedListeningTextState();
}

class _AnimatedListeningTextState extends State<_AnimatedListeningText> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        int dotCount = (_controller.value * 3).floor() + 1;
        final dots = '.' * dotCount + List.filled(3 - dotCount, '').join();

        return Text(
          '${widget.text}$dots',
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                color: widget.color,
                fontWeight: FontWeight.w800,
              ),
        );
      },
    );
  }
}

class _GlassmorphicStatPill extends StatelessWidget {
  const _GlassmorphicStatPill({
    required this.label,
    required this.value,
    required this.icon,
    required this.isActive,
  });

  final String label;
  final String value;
  final IconData icon;
  final bool isActive;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: isActive
                ? const Color(0xFFE91E63).withValues(alpha: 0.1)
                : Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: isActive
                  ? const Color(0xFFE91E63).withValues(alpha: 0.3)
                  : Colors.white.withValues(alpha: 0.2),
              width: 1.2,
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(
                icon,
                color: isActive ? const Color(0xFFE91E63) : Colors.grey.shade400,
                size: 20,
              ),
              const SizedBox(height: 4),
              Text(
                label,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: Colors.grey.shade600,
                      fontWeight: FontWeight.w500,
                    ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: isActive ? const Color(0xFFE91E63) : Colors.grey.shade700,
                      fontWeight: FontWeight.w700,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AssistantImagePlaceholder extends StatefulWidget {
  @override
  State<_AssistantImagePlaceholder> createState() => _AssistantImagePlaceholderState();
}

class _AssistantImagePlaceholderState extends State<_AssistantImagePlaceholder> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(duration: const Duration(seconds: 2), vsync: this)..repeat(reverse: true);
    _fadeAnimation = Tween<double>(begin: 0.8, end: 1.0).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: [
        FadeTransition(
          opacity: _fadeAnimation,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: SizedBox(
              width: 220,
              height: 220,
              child: Image.asset(
                'assets/images/assistant.png',
                fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) {
                    return Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Colors.pink.shade100, Colors.pink.shade50],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.person_4, size: 80, color: Colors.pink.shade300),
                          const SizedBox(height: 12),
                          Text(
                            'Your Assistant',
                            style: TextStyle(
                              color: Colors.pink.shade400,
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Save image to assets/images/assistant.png',
                            style: TextStyle(
                              color: Colors.pink.shade300,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ),
        ),
      ],
    );
  }
}

class _MicPulseIndicator extends StatefulWidget {
  const _MicPulseIndicator({required this.isListening});

  final bool isListening;

  @override
  State<_MicPulseIndicator> createState() => _MicPulseIndicatorState();
}

class _MicPulseIndicatorState extends State<_MicPulseIndicator> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1700),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final t = widget.isListening ? _controller.value : 0.0;

        return Container(
          height: 180,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(32),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Colors.white, Color(0xFFFFF1F7)],
            ),
            boxShadow: [
              BoxShadow(
                color: const Color(0x0AE91E63),
                blurRadius: 30,
                offset: const Offset(0, 15),
              )
            ],
            border: Border.all(color: const Color(0x33E91E63)),
          ),
          child: Center(
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (widget.isListening)
                  ...List.generate(3, (index) {
                    final delay = index * 0.33;
                    var phase = (t + delay) % 1.0;
                    phase = Curves.easeOutQuad.transform(phase);

                    return Container(
                      width: 86 + (phase * 120),
                      height: 86 + (phase * 120),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: const Color(0xFFE91E63).withValues(alpha: (1.0 - phase) * 0.6),
                          width: 2,
                        ),
                      ),
                    );
                  }),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  width: widget.isListening ? 96 : 86,
                  height: widget.isListening ? 96 : 86,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: widget.isListening ? const Color(0xFFE91E63) : Colors.white,
                    boxShadow: widget.isListening
                        ? [
                            BoxShadow(
                              color: const Color(0x66E91E63),
                              blurRadius: 30,
                              spreadRadius: 5,
                            )
                          ]
                        : null,
                    border: Border.all(
                      color: widget.isListening ? Colors.transparent : const Color(0x55E91E63),
                      width: 1.4,
                    ),
                  ),
                  child: Icon(
                    widget.isListening ? Icons.graphic_eq : Icons.mic_none,
                    size: widget.isListening ? 46 : 38,
                    color: widget.isListening ? Colors.white : const Color(0xFFC4A7B6),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _MicPulseIndicatorV2 extends StatefulWidget {
  const _MicPulseIndicatorV2({
    required this.isListening,
    required this.isBusy,
    required this.onTap,
  });

  final bool isListening;
  final bool isBusy;
  final VoidCallback onTap;

  @override
  State<_MicPulseIndicatorV2> createState() => _MicPulseIndicatorV2State();
}

class _MicPulseIndicatorV2State extends State<_MicPulseIndicatorV2>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1700),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.isBusy ? null : widget.onTap,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          final t = widget.isListening ? _controller.value : 0.0;

          return ClipRRect(
            borderRadius: BorderRadius.circular(40),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
              child: Container(
                height: 200,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(40),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Colors.white.withValues(alpha: 0.2),
                      const Color(0xFFFFF1F7).withValues(alpha: 0.1),
                    ],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFFE91E63).withValues(
                          alpha: widget.isListening ? 0.25 : 0.08),
                      blurRadius: 30,
                      spreadRadius: 2,
                      offset: const Offset(0, 12),
                    )
                  ],
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.3),
                    width: 1.5,
                  ),
                ),
                child: Center(
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      if (widget.isListening)
                        ...List.generate(3, (index) {
                          final delay = index * 0.33;
                          var phase = (t + delay) % 1.0;
                          phase = Curves.easeOutQuad.transform(phase);

                          return Container(
                            width: 96 + (phase * 140),
                            height: 96 + (phase * 140),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: const Color(0xFFE91E63).withValues(
                                    alpha: (1.0 - phase) * 0.5),
                                width: 2.5,
                              ),
                            ),
                          );
                        }),
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 300),
                        width: widget.isListening ? 110 : 100,
                        height: widget.isListening ? 110 : 100,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: widget.isListening
                              ? const LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    Color(0xFFE91E63),
                                    Color(0xFFFF80AB),
                                  ],
                                )
                              : LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    Colors.white,
                                    Color(0xFFFFF1F7),
                                  ],
                                ),
                          boxShadow: widget.isListening
                              ? [
                                  BoxShadow(
                                    color: const Color(0xFFE91E63)
                                        .withValues(alpha: 0.5),
                                    blurRadius: 35,
                                    spreadRadius: 8,
                                  )
                                ]
                              : [
                                  BoxShadow(
                                    color: const Color(0xFFE91E63)
                                        .withValues(alpha: 0.15),
                                    blurRadius: 20,
                                  )
                                ],
                          border: Border.all(
                            color: widget.isListening
                                ? Colors.transparent
                                : const Color(0x66E91E63),
                            width: 2,
                          ),
                        ),
                        child: Icon(
                          widget.isListening
                              ? Icons.graphic_eq_rounded
                              : Icons.mic_rounded,
                          size: widget.isListening ? 54 : 48,
                          color: widget.isListening ? Colors.white : const Color(0xFFE91E63),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({required this.text, required this.color});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Text(text),
    );
  }
}

// Splash/Loading Screen
class _LoadingScreen extends StatefulWidget {
  const _LoadingScreen();

  @override
  State<_LoadingScreen> createState() => _LoadingScreenState();
}

class _LoadingScreenState extends State<_LoadingScreen> with TickerProviderStateMixin {
  late AnimationController _scaleController;
  late AnimationController _rotateController;
  late Animation<double> _scaleAnimation;
  late Animation<double> _rotateAnimation;

  @override
  void initState() {
    super.initState();
    _scaleController = AnimationController(duration: const Duration(seconds: 2), vsync: this)..repeat(reverse: true);
    _rotateController = AnimationController(duration: const Duration(seconds: 3), vsync: this)..repeat();

    _scaleAnimation = Tween<double>(begin: 0.8, end: 1.1).animate(_scaleController);
    _rotateAnimation = Tween<double>(begin: 0, end: 2 * math.pi).animate(_rotateController);
  }

  @override
  void dispose() {
    _scaleController.dispose();
    _rotateController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFFFFF7FB), Color(0xFFFFEFF7), Color(0xFFFFE4F1)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ScaleTransition(
                scale: _scaleAnimation,
                child: Container(
                  width: 120,
                  height: 120,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white,
                    boxShadow: [
                      BoxShadow(
                        color: Color(0x44E91E63),
                        blurRadius: 30,
                        spreadRadius: 10,
                      ),
                    ],
                  ),
                  child: RotationTransition(
                    turns: _rotateAnimation,
                    child: const Icon(
                      Icons.favorite,
                      size: 60,
                      color: Color(0xFFE91E63),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 40),
              Text(
                'HeyyDarling',
                style: GoogleFonts.outfit(
                  fontSize: 32,
                  fontWeight: FontWeight.w800,
                  color: const Color(0xFFE91E63),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '😘',
                style: const TextStyle(fontSize: 48),
              ),
              const SizedBox(height: 40),
              SizedBox(
                width: 60,
                child: LinearProgressIndicator(
                  minHeight: 3,
                  backgroundColor: Colors.grey.shade200,
                  valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFFE91E63)),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Waking up your companion...',
                style: GoogleFonts.outfit(
                  fontSize: 14,
                  color: Colors.grey.shade600,
                  fontStyle: FontStyle.italic,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AssistantController extends ChangeNotifier {
  AssistantController(this._bridge);

  final HeyDarlingBridge _bridge;

  StreamSubscription<ServiceSnapshot>? _subscription;
  ServiceSnapshot _snapshot = ServiceSnapshot.initial();
  bool _isBusy = false;
  String? _permissionMessage;
  bool _isInitialized = false;

  ServiceSnapshot get snapshot => _snapshot;
  bool get isBusy => _isBusy;
  String? get permissionMessage => _permissionMessage;
  bool get isInitialized => _isInitialized;

  bool get _isAndroidPlatform => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  Timer? _autoRefreshTimer;

  void initialize() {
    _subscription = _bridge.statusStream.listen(
      (value) {
        _snapshot = value;
        _isInitialized = true;
        notifyListeners();
      },
      onError: (error) {
        _permissionMessage = error.toString();
        _isInitialized = true;
        notifyListeners();
      },
    );
    unawaited(refresh());
    // Auto-refresh every 2 minutes
    _autoRefreshTimer = Timer.periodic(const Duration(minutes: 2), (_) {
      unawaited(refresh());
    });
  }

  Future<void> refresh() async {
    if (!_isAndroidPlatform) {
      _snapshot = ServiceSnapshot.initial();
      _permissionMessage = 'This app currently supports Android devices only.';
      _isInitialized = true;
      notifyListeners();
      return;
    }

    final next = await _bridge.getSnapshot();
    _snapshot = next;
    _isInitialized = true;
    notifyListeners();
  }

  Future<void> start() async {
    if (!_isAndroidPlatform) {
      _permissionMessage = 'This app currently supports Android devices only.';
      notifyListeners();
      return;
    }

    _isBusy = true;
    _permissionMessage = null;
    notifyListeners();

    try {
      final ready = await _ensureAppReady();
      if (!ready) {
        return;
      }
      await _bridge.startService();
      await refresh();
    } on PlatformException catch (error) {
      _permissionMessage = error.message ?? error.code;
    } catch (error) {
      _permissionMessage = error.toString();
    } finally {
      _isBusy = false;
      notifyListeners();
    }
  }

  Future<void> stop() async {
    _isBusy = true;
    notifyListeners();
    try {
      await _bridge.stopService();
      await refresh();
    } finally {
      _isBusy = false;
      notifyListeners();
    }
  }

  Future<bool> _ensureAppReady() async {
    final statuses = await <Permission>[
      Permission.microphone,
      Permission.phone,
      Permission.notification,
    ].request();

    final denied = statuses.entries.where((entry) => !entry.value.isGranted).toList();
    if (denied.isNotEmpty) {
      final permanentlyDenied = denied.any((entry) => entry.value.isPermanentlyDenied);
      _permissionMessage = permanentlyDenied
          ? 'Permissions permanently denied. Grant microphone, phone, and notifications in app settings.'
          : 'Microphone, phone, and notification permissions are required.';
      notifyListeners();
      if (permanentlyDenied) {
        await openAppSettings();
      }
      return false;
    }

    final hasPolicyAccess = await _bridge.hasNotificationPolicyAccess();
    if (!hasPolicyAccess) {
      _permissionMessage = 'Allow notification policy access so ringer mode can be changed.';
      notifyListeners();
      await _bridge.requestNotificationPolicyAccess();
      return false;
    }

    final hasNotificationListener = await _bridge.hasNotificationListenerAccess();
    if (!hasNotificationListener) {
      _permissionMessage = 'Enable Notification Access for WhatsApp and VoIP call detection.';
      notifyListeners();
      await _bridge.requestNotificationListenerAccess();
      return false;
    }

    final ignoringBattery = await _bridge.isIgnoringBatteryOptimizations();
    if (!ignoringBattery) {
      _permissionMessage = 'Disable battery optimization for stable background listening.';
      notifyListeners();
      await _bridge.requestIgnoreBatteryOptimizations();
      return false;
    }

    return true;
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _autoRefreshTimer?.cancel();
    super.dispose();
  }
}

class HeyDarlingBridge {
  static const MethodChannel _methodChannel = MethodChannel('com.example.silentoapp/service');
  static const EventChannel _eventChannel = EventChannel('com.example.silentoapp/service_status');

  Stream<ServiceSnapshot> get statusStream => _eventChannel.receiveBroadcastStream().map((event) => ServiceSnapshot.fromMap(Map<String, dynamic>.from(event)));

  Future<void> startService() => _methodChannel.invokeMethod('startService');
  Future<void> stopService() => _methodChannel.invokeMethod('stopService');

  Future<ServiceSnapshot> getSnapshot() async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>('getServiceSnapshot');
    return ServiceSnapshot.fromMap(result ?? const <String, dynamic>{});
  }

  Future<bool> hasNotificationPolicyAccess() async {
    final result = await _methodChannel.invokeMethod<bool>('hasNotificationPolicyAccess');
    return result ?? false;
  }

  Future<void> requestNotificationPolicyAccess() => _methodChannel.invokeMethod('requestNotificationPolicyAccess');

  Future<bool> hasNotificationListenerAccess() async {
    final result = await _methodChannel.invokeMethod<bool>('hasNotificationListenerAccess');
    return result ?? false;
  }

  Future<void> requestNotificationListenerAccess() => _methodChannel.invokeMethod('requestNotificationListenerAccess');

  Future<bool> isIgnoringBatteryOptimizations() async {
    final result = await _methodChannel.invokeMethod<bool>('isIgnoringBatteryOptimizations');
    return result ?? false;
  }

  Future<void> requestIgnoreBatteryOptimizations() => _methodChannel.invokeMethod('requestIgnoreBatteryOptimizations');
}

class ServiceSnapshot {
  const ServiceSnapshot({
    required this.status,
    required this.isRunning,
    required this.pendingMode,
    required this.logs,
    this.lastCommand,
    this.lastTranscript,
  });

  factory ServiceSnapshot.initial() {
    return const ServiceSnapshot(
      status: 'idle',
      isRunning: false,
      pendingMode: 'none',
      logs: <String>[],
    );
  }

  factory ServiceSnapshot.fromMap(Map<String, dynamic> map) {
    return ServiceSnapshot(
      status: map['status'] as String? ?? 'idle',
      isRunning: map['isRunning'] as bool? ?? false,
      pendingMode: map['pendingMode'] as String? ?? 'none',
      lastCommand: map['lastCommand'] as String?,
      lastTranscript: map['lastTranscript'] as String?,
      logs: (map['logs'] as List<dynamic>? ?? const <dynamic>[]).map((item) => item.toString()).toList(growable: false),
    );
  }


  final String status;
  final bool isRunning;
  final String pendingMode;
  final String? lastCommand;
  final String? lastTranscript;
  final List<String> logs;

  String get statusLabel => switch (status) {
        'listening' => 'Listening',
        'silent_triggered' => 'Silent Activated',
        'vibrate_triggered' => 'Vibrate Activated',
        'starting' => 'Booting Assistant',
        'error' => 'Recovery Mode',
        _ => 'Idle',
      };

  String get pendingModeLabel => switch (pendingMode) {
        'silent' => 'Silent on next call',
        'vibrate' => 'Vibrate on next call',
        _ => 'None',
      };

  Color get statusColor => switch (status) {
        'listening' => const Color(0xFFE91E63),
        'silent_triggered' => const Color(0xFFC2185B),
        'vibrate_triggered' => const Color(0xFFAD1457),
        'error' => const Color(0xFFD81B60),
        _ => const Color(0xFFEC407A),
      };
}
