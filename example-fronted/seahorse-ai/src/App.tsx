import { useState, useRef, useEffect } from 'react';
import { 
  Plus, 
  Search, 
  Mic, 
  Send, 
  BrainCircuit, 
  User, 
  Settings, 
  BookOpen, 
  ListTodo, 
  Lightbulb,
  MessageSquare,
  Sparkles,
  ChevronRight
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

interface Message {
  role: 'user' | 'model';
  content: string;
}

export default function App() {
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isThinking, setIsThinking] = useState(false);
  const [deepThinking, setDeepThinking] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isThinking]);

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage: Message = { role: 'user', content: input };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsThinking(true);

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          message: input,
          history: messages.map(m => ({ 
            role: m.role, 
            parts: [{ text: m.content }] 
          }))
        }),
      });

      const data = await response.json();
      if (data.text) {
        setMessages(prev => [...prev, { role: 'model', content: data.text }]);
      } else {
        throw new Error(data.error || 'No response from AI');
      }
    } catch (error) {
      console.error('Error:', error);
      setMessages(prev => [...prev, { role: 'model', content: 'I encountered an error while processing your request. Please try again.' }]);
    } finally {
      setIsThinking(false);
    }
  };

  const FeatureCard = ({ icon: Icon, title, description }: { icon: any, title: string, description: string }) => (
    <motion.div 
      whileHover={{ y: -5 }}
      className="glass glass-hover p-5 rounded-2xl glow-border flex flex-col gap-3 group cursor-pointer"
    >
      <div className="p-3 rounded-xl bg-cyan-500/10 w-fit text-cyan-400 group-hover:text-cyan-300 transition-colors">
        <Icon size={24} />
      </div>
      <div>
        <h3 className="font-semibold text-lg text-white glow-text mb-1">{title}</h3>
        <p className="text-sm text-slate-400 line-clamp-2">{description}</p>
      </div>
    </motion.div>
  );

  return (
    <div className="flex h-screen w-full overflow-hidden font-sans">
      {/* Sidebar */}
      <aside className="w-80 glass border-r border-cyan-500/10 flex flex-col p-6 h-full z-10 transition-all">
        <div className="flex flex-col items-center gap-4 mb-10">
          <div className="relative">
            <div className="absolute inset-0 bg-cyan-500/20 blur-2xl rounded-full animate-pulse" />
            <div className="relative bg-marine-900 border border-cyan-500/20 p-4 rounded-3xl shadow-2xl">
              <BrainCircuit className="text-cyan-400" size={48} />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-white glow-text tracking-tight">Seahorse Agent</h1>
        </div>

        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="flex items-center justify-between w-full p-4 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 text-white font-medium mb-8 hover:bg-cyan-500/20 transition-all group overflow-hidden relative"
        >
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-cyan-500/5 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000" />
          <div className="flex items-center gap-3">
            <span className="p-1 rounded-lg bg-cyan-500/20">
              <Plus size={20} className="text-cyan-400" />
            </span>
            <span>New Chat</span>
          </div>
          <div className="opacity-0 group-hover:opacity-100 transition-opacity">
            <div className="bg-marine-800 p-1 rounded-md border border-cyan-500/20">
               <ChevronRight size={16} className="text-cyan-400" />
            </div>
          </div>
        </motion.button>

        <div className="relative mb-8">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
          <input 
            type="text" 
            placeholder="Search chats..." 
            className="w-full bg-marine-900/50 border border-cyan-500/10 rounded-xl py-3 pl-12 pr-4 text-sm focus:outline-none focus:border-cyan-500/30 focus:bg-marine-900/80 transition-all text-slate-300"
          />
        </div>

        <div className="flex-1 overflow-y-auto pr-2 space-y-4">
          <h2 className="text-xs uppercase tracking-widest text-slate-500 font-bold px-2">Chat History</h2>
          <div className="text-center py-10 text-slate-600 text-sm italic">
            No chats yet
          </div>
        </div>

        <div className="mt-auto flex items-center justify-between pt-6 border-t border-cyan-500/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-cyan-500/20 border border-cyan-500/20 flex items-center justify-center">
              <User size={20} className="text-cyan-400" />
            </div>
            <div>
              <p className="text-sm font-medium text-white">Guest User</p>
              <p className="text-xs text-slate-500">Free Tier</p>
            </div>
          </div>
          <button className="text-slate-500 hover:text-cyan-400 transition-colors">
            <Settings size={20} />
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col relative overflow-hidden bg-marine-950">
        {/* Top bar icons */}
        <div className="absolute top-6 right-8 flex items-center gap-4 z-20">
          <button className="p-2.5 rounded-xl glass glass-hover text-slate-400 hover:text-cyan-400">
            <User size={20} />
          </button>
          <button className="p-2.5 rounded-xl glass glass-hover text-slate-400 hover:text-cyan-400">
            <Settings size={20} />
          </button>
        </div>

        {/* Viewport */}
        <div className="flex-1 overflow-y-auto px-10 pb-40 pt-10 scroll-smooth" ref={scrollRef}>
          {messages.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center max-w-4xl mx-auto w-full">
              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center text-center mb-16"
              >
                <div className="relative mb-10">
                  <div className="absolute inset-0 bg-cyan-500/20 blur-3xl rounded-full scale-110" />
                  <motion.div 
                    animate={{ y: [0, -10, 0] }}
                    transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
                    className="relative bg-marine-900/50 border border-cyan-500/20 p-10 rounded-[4rem] shadow-2xl backdrop-blur-xl"
                  >
                    <BrainCircuit className="text-cyan-400" size={100} />
                  </motion.div>
                </div>
                <h2 className="text-5xl font-bold text-white mb-3 glow-text tracking-tight">Seahorse AI</h2>
                <p className="text-xl text-cyan-400/80 font-medium tracking-wide">Intelligent Chat Emulator</p>
              </motion.div>

              <div className="grid grid-cols-3 gap-6 w-full">
                <FeatureCard 
                  icon={BookOpen}
                  title="Research & Synthesis"
                  description="Summarize complex topics with deep insights into research materials."
                />
                <FeatureCard 
                  icon={ListTodo}
                  title="Task Analysis"
                  description="Break down goals into actionable steps and tactical neural tasks."
                />
                <FeatureCard 
                  icon={Lightbulb}
                  title="Creative Expansion"
                  description="Generate ideas and explore new perspectives with creative smartness."
                />
              </div>
            </div>
          ) : (
            <div className="max-w-4xl mx-auto w-full space-y-8 mt-16">
              {messages.map((message, idx) => (
                <motion.div 
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  key={idx}
                  className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  <div className={`max-w-[80%] rounded-2xl p-5 ${
                    message.role === 'user' 
                      ? 'bg-cyan-500/10 border border-cyan-500/30 text-white' 
                      : 'glass text-slate-200'
                  }`}>
                    {message.role === 'model' && (
                      <div className="flex items-center gap-2 mb-3 text-cyan-400">
                        <Sparkles size={16} />
                        <span className="text-xs font-bold uppercase tracking-widest">Seahorse AI</span>
                      </div>
                    )}
                    <div className="prose prose-invert max-w-none text-base leading-relaxed">
                      {message.content}
                    </div>
                  </div>
                </motion.div>
              ))}
              {isThinking && (
                <motion.div 
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="flex justify-start"
                >
                  <div className="glass rounded-2xl p-5 flex items-center gap-3">
                    <div className="flex gap-1.5">
                      <motion.div animate={{ opacity: [0.3, 1, 0.3] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0 }} className="w-2 h-2 rounded-full bg-cyan-400" />
                      <motion.div animate={{ opacity: [0.3, 1, 0.3] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0.2 }} className="w-2 h-2 rounded-full bg-cyan-400" />
                      <motion.div animate={{ opacity: [0.3, 1, 0.3] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0.4 }} className="w-2 h-2 rounded-full bg-cyan-400" />
                    </div>
                    <span className="text-xs font-bold text-cyan-400/60 uppercase tracking-widest">Deep thinking...</span>
                  </div>
                </motion.div>
              )}
            </div>
          )}
        </div>

        {/* Input Area */}
        <div className="absolute bottom-0 left-0 right-0 p-10 bg-gradient-to-t from-marine-950 via-marine-950 to-transparent">
          <div className="max-w-4xl mx-auto relative group">
            <div className="absolute -inset-1 bg-gradient-to-r from-cyan-500/20 to-blue-500/20 rounded-3xl blur opacity-30 group-focus-within:opacity-60 transition-opacity" />
            <div className="relative glass rounded-3xl glow-border p-2">
              <div className="flex flex-col gap-2 p-3">
                <textarea 
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), handleSend())}
                  placeholder="Ask me anything about research, analysis, or creative tasks..."
                  className="w-full bg-transparent border-none resize-none focus:outline-none text-white text-lg placeholder-slate-500 px-3 py-2 min-h-[60px]"
                />
                <div className="flex items-center justify-between mt-2 border-t border-cyan-500/5 pt-4">
                  <div className="flex items-center gap-6">
                    <button className="text-slate-500 hover:text-cyan-400 transition-colors">
                      <Mic size={22} />
                    </button>
                    
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Deep Thinking</span>
                      <button 
                        onClick={() => setDeepThinking(!deepThinking)}
                        className={`w-12 h-6 rounded-full relative transition-colors duration-300 ${
                          deepThinking ? 'bg-cyan-500/40' : 'bg-marine-800'
                        } border border-cyan-500/20`}
                      >
                        <motion.div 
                          animate={{ x: deepThinking ? 24 : 4 }}
                          className={`absolute top-1 w-4 h-4 rounded-full bg-cyan-400 shadow-[0_0_10px_rgba(6,182,212,0.6)]`}
                        />
                      </button>
                    </div>
                  </div>

                  <motion.button 
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    onClick={handleSend}
                    disabled={!input.trim() || isThinking}
                    className="bg-cyan-500 hover:bg-cyan-400 text-marine-950 w-14 h-14 rounded-2xl flex items-center justify-center transition-all shadow-lg shadow-cyan-500/20 disabled:opacity-50 disabled:cursor-not-allowed group"
                  >
                    <Send size={24} className="group-hover:rotate-12 transition-transform" />
                  </motion.button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

