import { BrowserRouter, Link, Routes, Route } from 'react-router';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { ProtectedRoute } from './features/auth/components/ProtectedRoute';
import { CreatePostPage } from './pages/CreatePostPage';

function App() {

  return (
    <BrowserRouter>
      <nav className="flex gap-4 border-b p-4">
        <Link to="/" className="hover:underline">Home</Link>
        <Link to="/login" className="hover:underline">Login</Link>
      </nav>

      <main className="p-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="*" element={<h1>404 — Page Not Found</h1>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/posts/create" element={<CreatePostPage />} />
          </Route>
        </Routes>
      </main>
    </BrowserRouter>
  )
}

export default App